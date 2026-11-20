#!/usr/bin/env python3
"""Generate original, CC0-dedicated, small GLB building blocks.

No external Python dependencies or third-party models. Generated pack uses a
ZIP container of standalone glTF 2.0 GLBs and a small manifest. The Android app
reads only the manifest when showing its catalog and extracts a selected GLB
directly into memory when previewing or inserting it.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import struct
import zipfile

class Mesh:
    def __init__(self):
        self.data = []

    def tri(self, a, b, c):
        ab = [b[i]-a[i] for i in range(3)]
        ac = [c[i]-a[i] for i in range(3)]
        n = [ab[1]*ac[2]-ab[2]*ac[1], ab[2]*ac[0]-ab[0]*ac[2],
             ab[0]*ac[1]-ab[1]*ac[0]]
        m = math.sqrt(sum(x*x for x in n))
        if m < 1e-8:
            return
        n = [v/m for v in n]
        for p in (a, b, c):
            self.data.extend((*p, *n))

    def quad(self, a, b, c, d):
        self.tri(a, b, c)
        self.tri(a, c, d)

    def box(self, width, height, depth, center=(0, 0, 0)):
        cx,cy,cz=center
        x=width/2;y=height/2;z=depth/2
        p=[(cx-x,cy-y,cz-z),(cx+x,cy-y,cz-z),
           (cx+x,cy+y,cz-z),(cx-x,cy+y,cz-z),
           (cx-x,cy-y,cz+z),(cx+x,cy-y,cz+z),
           (cx+x,cy+y,cz+z),(cx-x,cy+y,cz+z)]
        for a,b,c,d in [(0,3,2,1),(4,5,6,7),(0,4,7,3),
                        (1,2,6,5),(3,7,6,2),(0,1,5,4)]:
            self.quad(p[a],p[b],p[c],p[d])
        return self

    def tapered(self, width,height,depth,topwidth=0.75,topdepth=0.82):
        x=width/2;z=depth/2;y=height/2
        xt=x*topwidth;zt=z*topdepth
        p=[(-x,-y,-z),(x,-y,-z),(xt,y,-zt),(-xt,y,-zt),
           (-x,-y,z),(x,-y,z),(xt,y,zt),(-xt,y,zt)]
        for a,b,c,d in [(0,3,2,1),(4,5,6,7),(0,4,7,3),
                        (1,2,6,5),(3,7,6,2),(0,1,5,4)]:
            self.quad(p[a],p[b],p[c],p[d])
        return self

    def cylinder(self, radius,height,segments=20,topradius=None,y0=None,cap=True):
        if topradius is None:topradius=radius
        if y0 is None:y0=-height/2
        low=y0;high=y0+height
        for i in range(segments):
            t=2*math.pi*i/segments
            u=2*math.pi*(i+1)/segments
            a=(radius*math.cos(t),low,radius*math.sin(t))
            b=(radius*math.cos(u),low,radius*math.sin(u))
            c=(topradius*math.cos(u),high,topradius*math.sin(u))
            d=(topradius*math.cos(t),high,topradius*math.sin(t))
            self.quad(a,d,c,b)
            if cap:
                self.tri((0,low,0),b,a)
                self.tri((0,high,0),d,c)
        return self

    def tube(self,outer,inner,height,segments=24,y0=None):
        if y0 is None:y0=-height/2
        low=y0;high=y0+height
        for i in range(segments):
            t=2*math.pi*i/segments;u=2*math.pi*(i+1)/segments
            def p(r,theta,h):return (r*math.cos(theta),h,r*math.sin(theta))
            a,b,c,d=p(outer,t,low),p(outer,u,low),p(outer,u,high),p(outer,t,high)
            e,f,g,h=p(inner,t,low),p(inner,u,low),p(inner,u,high),p(inner,t,high)
            self.quad(a,d,c,b);self.quad(e,f,g,h)
            self.quad(d,h,g,c);self.quad(a,b,f,e)
        return self

    def torus(self,major,minor,segments=24,sections=10):
        def p(t,u):
            radius=major+minor*math.cos(u)
            return (radius*math.cos(t),minor*math.sin(u),radius*math.sin(t))
        for i in range(segments):
            t=2*math.pi*i/segments;tn=2*math.pi*(i+1)/segments
            for j in range(sections):
                a=2*math.pi*j/sections;b=2*math.pi*(j+1)/sections
                self.quad(p(t,a),p(tn,a),p(tn,b),p(t,b))
        return self

    def sphere(self,radius=0.5,segments=20,rings=12):
        for j in range(rings):
            v0=-math.pi/2+math.pi*j/rings
            v1=-math.pi/2+math.pi*(j+1)/rings
            for i in range(segments):
                t=2*math.pi*i/segments
                u=2*math.pi*(i+1)/segments
                def p(v,a):return (radius*math.cos(v)*math.cos(a),
                                   radius*math.sin(v),
                                   radius*math.cos(v)*math.sin(a))
                self.quad(p(v0,t),p(v1,t),p(v1,u),p(v0,u))
        return self

    def wedge(self,width=1,height=0.5,depth=1.5):
        x=width/2;z=depth/2;y=height/2
        p=[(-x,-y,-z),(x,-y,-z),(-x,-y,z),(x,-y,z),(-x,y,z),(x,y,z)]
        for tri in [(0,2,3),(0,3,1),(2,4,5),(2,5,3),
                    (0,1,5),(0,5,4),(0,4,2),(1,3,5)]:
            self.tri(*(p[i] for i in tri))
        return self

def make_asset(asset_id):
    m=Mesh()
    if asset_id=='cube':m.box(1,1,1)
    elif asset_id=='plate':m.box(1.8,0.12,1.3)
    elif asset_id=='sphere':m.sphere(0.6)
    elif asset_id=='cylinder':m.cylinder(0.5,1.3,28)
    elif asset_id=='cone':m.cylinder(0.6,1.2,28,topradius=0)
    elif asset_id=='pipe':m.tube(0.55,0.39,1.5)
    elif asset_id=='torus':m.torus(0.62,0.16)
    elif asset_id=='wedge':m.wedge()
    elif asset_id=='fairing':m.tapered(1.5,0.52,2.3,0.68,0.6)
    elif asset_id=='armour_panel':m.tapered(1.5,0.20,1.8,0.86,0.87)
    elif asset_id=='aero_fin':m.wedge(0.18,0.95,1.6)
    elif asset_id=='nozzle':
        m.tube(0.58,0.37,0.43)
        m.torus(0.46,0.07)
    elif asset_id=='thruster_housing':
        m.tapered(0.96,0.85,1.75,0.88,0.93)
        m.tube(0.4,0.28,0.18,y0=-0.53)
    elif asset_id=='bolt':
        m.cylinder(0.15,0.95,12,y0=-0.45)
        m.cylinder(0.32,0.23,6,y0=0.50)
    elif asset_id=='hex_nut':m.tube(0.48,0.23,0.25,6)
    elif asset_id=='radiator_grille':
        for i in range(7):m.box(1.55,0.075,0.07,(0,0,(i-3)*0.23))
        for x in (-0.78,0.78):m.box(0.09,0.1,1.52,(x,0,0))
    elif asset_id=='intake_frame':
        m.box(1.2,0.12,0.13,(0,0,-0.5))
        m.box(1.2,0.12,0.13,(0,0,0.5))
        for x in (-0.55,0.55):m.box(0.12,0.12,1.15,(x,0,0))
        for x in (-0.3,0.0,0.3):m.box(0.045,0.1,1.0,(x,0,0))
    elif asset_id=='hub':
        m.cylinder(0.6,0.28,24)
        m.cylinder(0.27,0.34,16)
        m.torus(0.45,0.085,24,8)
    else:raise ValueError('Unknown primitive '+asset_id)
    if not m.data:raise ValueError('No mesh data')
    return m

def as_glb(asset_id,label,rgba,mesh):
    d=mesh.data
    positions=[d[i:i+3] for i in range(0,len(d),6)]
    low=[min(p[k] for p in positions) for k in range(3)]
    high=[max(p[k] for p in positions) for k in range(3)]
    packed=struct.pack('<'+'f'*len(d),*d)
    definition={
        'asset':{'version':'2.0','generator':'Model Forge Original CC0 Basics'},
        'scene':0,'scenes':[{'nodes':[0]}],
        'nodes':[{'mesh':0,'name':label}],
        'meshes':[{'name':label,'primitives':[{
            'attributes':{'POSITION':0,'NORMAL':1},'material':0,'mode':4
        }]}],
        'materials':[{'name':'Forge Basic '+label,'doubleSided':True,
            'pbrMetallicRoughness':{'baseColorFactor':rgba,
            'metallicFactor':0.38,'roughnessFactor':0.45}}],
        'buffers':[{'byteLength':len(packed)}],
        'bufferViews':[{'buffer':0,'byteOffset':0,'byteLength':len(packed),
                        'byteStride':24,'target':34962}],
        'accessors':[
            {'bufferView':0,'componentType':5126,'count':len(positions),
             'type':'VEC3','min':low,'max':high},
            {'bufferView':0,'byteOffset':12,'componentType':5126,
             'count':len(positions),'type':'VEC3'}
        ]
    }
    j=json.dumps(definition,separators=(',',':'),sort_keys=True).encode('utf8')
    j+=b' '*((-len(j))%4)
    packed+=b'\0'*((-len(packed))%4)
    total=12+8+len(j)+8+len(packed)
    return (struct.pack('<III',0x46546c67,2,total)+
            struct.pack('<II',len(j),0x4e4f534a)+j+
            struct.pack('<II',len(packed),0x004e4942)+packed)

BASICS=[
('cube','Cube','Primitive'),('plate','Flat plate','Primitive'),
('sphere','UV sphere','Primitive'),('cylinder','Cylinder','Primitive'),
('cone','Cone','Primitive'),('pipe','Hollow pipe','Primitive'),
('torus','Torus ring','Primitive'),('wedge','Wedge','Primitive'),
('fairing','Tapered fairing','Bodywork'),
('armour_panel','Beveled armor blank','Bodywork'),
('aero_fin','Aero fin','Bodywork'),
('nozzle','Circular nozzle','Propulsion'),
('thruster_housing','Thruster housing','Propulsion'),
('bolt','Hex-head bolt','Mechanical'),
('hex_nut','Hex nut','Mechanical'),
('radiator_grille','Vent grille','Bodywork'),
('intake_frame','Intake surround','Bodywork'),
('hub','Wheel or thruster hub','Mechanical')
]

def build(output):
    color=[0.32,0.52,0.65,1.0]
    manifest={'format':'model-forge-pack','version':1,
              'pack_id':'forge-basics-cc0','title':'Forge Basics',
              'license':'CC0-1.0','origin':'Model Forge generated original geometry',
              'assets':[]}
    output=Path(output)
    output.parent.mkdir(parents=True,exist_ok=True)
    temp=output.with_suffix('.tmp')
    with zipfile.ZipFile(temp,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for asset_id,title,category in BASICS:
            glb=as_glb(asset_id,title,color,make_asset(asset_id))
            # Verify the generated GLB header before shipping.
            assert struct.unpack_from('<III',glb)==(0x46546c67,2,len(glb))
            path='glbs/'+asset_id+'.glb'
            archive.writestr(path,glb)
            manifest['assets'].append({
                'id':asset_id,'title':title,'category':category,
                'file':path,'bytes':len(glb),'sha256':hashlib.sha256(glb).hexdigest(),
                'license':'CC0-1.0','source':'original procedural mesh'})
        archive.writestr('manifest.json',json.dumps(manifest,sort_keys=True,separators=(',',':')))
    os.replace(temp,output)
    with zipfile.ZipFile(output) as check:
        for asset in manifest['assets']:
            assert len(check.read(asset['file']))==asset['bytes']
    raw=sum(x['bytes'] for x in manifest['assets'])
    print('FORGE_BASICS: PASS items=%s uncompressed=%s compressed=%s path=%s' %
          (len(BASICS),raw,output.stat().st_size,output))

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',required=True)
    args=parser.parse_args()
    build(args.output)
