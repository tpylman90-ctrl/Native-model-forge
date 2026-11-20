#!/usr/bin/env python3
"""Convert a curated set of Blender-exported CC0 glTF 2.0 GLBs to an .mfpack.

Every object must have been reviewed for its individual license. This is NOT
a BlenderKit scraper or a bulk license bypass. Complex textured/animated files
must be simplified/exported for the first version of Model Forge's importer.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile

MAX_OBJECT=8*1024*1024
MAX_PACK=80*1024*1024

def inspect_glb(raw):
    if len(raw)<28 or len(raw)>MAX_OBJECT:
        raise ValueError('GLB size exceeds 8 MB')
    magic,version,length=struct.unpack_from('<III',raw)
    if (magic,version,length)!=(0x46546c67,2,len(raw)):
        raise ValueError('Invalid GLB header')
    jsonlen,chunk=struct.unpack_from('<II',raw,12)
    if chunk!=0x4e4f534a or jsonlen<4 or jsonlen>1024*1024:
        raise ValueError('Invalid GLB JSON chunk')
    doc=json.loads(raw[20:20+jsonlen].decode('utf8').rstrip(' \x00'))
    offset=20+jsonlen
    if offset+8>len(raw) or struct.unpack_from('<I',raw,offset+4)[0]!=0x004e4942:
        raise ValueError('GLB must embed its BIN chunk')
    binlen=struct.unpack_from('<I',raw,offset)[0]
    if offset+8+binlen!=len(raw):
        raise ValueError('Invalid GLB BIN size')
    if len(doc.get('meshes',[]))!=1 or len(doc['meshes'][0].get('primitives',[]))!=1:
        raise ValueError('Export as one mesh with one primitive')
    primitive=doc['meshes'][0]['primitives'][0]
    if primitive.get('mode',4)!=4:
        raise ValueError('Only triangle meshes supported')
    if 'POSITION' not in primitive.get('attributes',{}):
        raise ValueError('Missing POSITION attribute')
    if len(doc.get('nodes',[]))!=1:
        raise ValueError('Export a single GLB object')
    if any(n in doc['nodes'][0] for n in ('matrix','translation','rotation','scale','skin')):
        raise ValueError('Apply transforms before exporting from Blender')
    if doc.get('images') or doc.get('textures'):
        raise ValueError('Texture/image GLBs are not yet supported by this lightweight importer')
    for attr in ('POSITION','NORMAL'):
        index=primitive['attributes'].get(attr)
        if index is None:continue
        a=doc['accessors'][index]
        if a.get('componentType')!=5126 or a.get('type')!='VEC3' or 'sparse' in a:
            raise ValueError('Export unquantized float32 VEC3 coordinates/normals')
    if primitive.get('material',-1)>=0:
        pbr=doc.get('materials',[{}])[primitive['material']].get('pbrMetallicRoughness',{})
        if 'baseColorTexture' in pbr:
            raise ValueError('The first library version accepts solid-color materials only')
    return doc

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--manifest',required=True,help='JSON pack source manifest')
    parser.add_argument('--output',required=True,help='ZIP-based .mfpack output')
    args=parser.parse_args()
    path=Path(args.manifest).resolve()
    spec=json.loads(path.read_text(encoding='utf8'))
    pack_id=spec['pack_id']
    if not re.fullmatch(r'[A-Za-z0-9_-]{2,80}',pack_id):
        raise ValueError('Invalid pack ID')
    if not spec['title'] or len(spec['title'])>90:
        raise ValueError('Invalid pack title')
    objects=spec['assets']
    if not 1<=len(objects)<=500:
        raise ValueError('Pack must have 1–500 assets')
    manifest={'format':'model-forge-pack','version':1,
              'pack_id':pack_id,'title':spec['title'],
              'license':'asset-specific','origin':'Blender exported CC0 sources',
              'assets':[]}
    out=Path(args.output)
    out.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as zipout:
        for item in objects:
            asset_id=item['id']
            if not re.fullmatch('[A-Za-z0-9_-]{1,75}',asset_id):
                raise ValueError('Invalid asset id')
            if item.get('license')!='CC0-1.0':
                raise ValueError('Only independently VERIFIED CC0-1.0 assets may be repacked')
            if not item.get('source'):
                raise ValueError('Every asset needs a source URL or author/source record')
            model=Path(item['path'])
            if not model.is_absolute():model=path.parent/model
            raw=model.read_bytes()
            inspect_glb(raw)
            member='glbs/'+asset_id+'.glb'
            zipout.writestr(member,raw)
            manifest['assets'].append({
                'id':asset_id,'title':item['title'],
                'category':item.get('category','Blender CC0'),
                'file':member,'bytes':len(raw),
                'sha256':hashlib.sha256(raw).hexdigest(),
                'license':'CC0-1.0','source':str(item['source'])[:200]
            })
        zipout.writestr('manifest.json',json.dumps(manifest,sort_keys=True,separators=(',',':')))
    if out.stat().st_size>MAX_PACK:
        out.unlink(missing_ok=True)
        raise ValueError('Compressed pack exceeds 80 MB')
    print('BLENDER_GLB_PACK: PASS assets=%d bytes=%d' %
          (len(manifest['assets']),out.stat().st_size))

if __name__=='__main__':
    main()
