/**
 * Imported GLB workshop for Model Forge browser Studio.
 *
 * Browser equivalent of the Android GlbWorkshop/GlbMeshDeform subset:
 * lossless JSON+BIN preservation, per-node transforms, and a localized
 * vertex brush, with clone-on-write when different nodes share a mesh.
 *
 * The original mesh buffers are never modified; new position/normal
 * accessors are appended, which makes compact JSON+length undo possible.
 * This is deliberately distinct from the C++ native parametric box engine:
 * imported-glTF topology isn't yet part of native Document/MFORGE_DOC.
 */
const MAX_FILE = 80 * 1024 * 1024;
const MAX_JSON = 12 * 1024 * 1024;
const HEADER_MAGIC = 0x46546c67, JSON_MAGIC = 0x4e4f534a, BIN_MAGIC = 0x004e4942;
const enc = new TextEncoder(), dec = new TextDecoder("utf-8", {fatal:true});
const clone = (object) => JSON.parse(JSON.stringify(object));
const finite = (value,name) => {
  if(typeof value!=="number" || !Number.isFinite(value))throw Error(name+" must be a finite number");
  return value;
};
function validateArray(value,count,name) {
  if(!Array.isArray(value) || value.length!==count)
    throw Error(name+" must contain "+count+" values");
  return value.map(v=>finite(v,name));
}
function align4(n){return (n+3)&~3;}
function requireRange(value,min,max,name){
  if(!Number.isInteger(value) || value<min || value>max)throw Error(name+" is outside valid range");
}
function identity(){return [1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1];}
function multiply(a,b){
  const r=new Array(16).fill(0);
  for(let c=0;c<4;c++)for(let row=0;row<4;row++)for(let k=0;k<4;k++)
    r[c*4+row]+=a[k*4+row]*b[c*4+k];
  return r;
}
function translation(x,y,z){const m=identity();m[12]=x;m[13]=y;m[14]=z;return m;}
function scaling(x,y,z){const m=identity();m[0]=x;m[5]=y;m[10]=z;return m;}
function rotation(x,y,z){
  const [a,b,c]=[x,y,z].map(v=>v*Math.PI/180);
  const rx=identity(), ry=identity(), rz=identity();
  rx[5]=Math.cos(a);rx[6]=Math.sin(a);rx[9]=-Math.sin(a);rx[10]=Math.cos(a);
  ry[0]=Math.cos(b);ry[2]=-Math.sin(b);ry[8]=Math.sin(b);ry[10]=Math.cos(b);
  rz[0]=Math.cos(c);rz[1]=Math.sin(c);rz[4]=-Math.sin(c);rz[5]=Math.cos(c);
  return multiply(multiply(rz,ry),rx);
}
function currentMatrix(node) {
  if(node.matrix) return validateArray(node.matrix,16,"node matrix");
  const pos=validateArray(node.translation||[0,0,0],3,"translation");
  const s=validateArray(node.scale||[1,1,1],3,"node scale");
  const q=validateArray(node.rotation||[0,0,0,1],4,"node quaternion");
  let n=Math.hypot(...q);
  if(n<1e-7)throw Error("Invalid node rotation quaternion");
  const [x,y,z,w]=q.map(v=>v/n);
  const r=identity();
  r[0]=1-2*(y*y+z*z);r[1]=2*(x*y+z*w);r[2]=2*(x*z-y*w);
  r[4]=2*(x*y-z*w);r[5]=1-2*(x*x+z*z);r[6]=2*(y*z+x*w);
  r[8]=2*(x*z+y*w);r[9]=2*(y*z-x*w);r[10]=1-2*(x*x+y*y);
  return multiply(multiply(translation(...pos),r),scaling(...s));
}
function parseGLB(input) {
  const bytes=input instanceof Uint8Array ? input : new Uint8Array(input);
  if(bytes.length<28 || bytes.length>MAX_FILE)throw Error("GLB must be 28 bytes to 80 MB");
  const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
  if(view.getUint32(0,true)!==HEADER_MAGIC || view.getUint32(4,true)!==2 ||
     view.getUint32(8,true)!==bytes.length)throw Error("Not a complete glTF 2.0 binary model");
  const jsonLength=view.getUint32(12,true);
  if(jsonLength<4 || jsonLength>MAX_JSON || jsonLength%4 ||
     jsonLength>bytes.length-20 || view.getUint32(16,true)!==JSON_MAGIC)
    throw Error("Invalid GLB JSON chunk");
  let json;
  try{json=JSON.parse(dec.decode(bytes.subarray(20,20+jsonLength)).trimEnd());}
  catch(e){throw Error("Cannot parse GLB document: "+e.message);}
  let offset=20+jsonLength, bin=new Uint8Array();
  if(offset<bytes.length){
    if(offset+8>bytes.length)throw Error("Truncated GLB BIN header");
    const len=view.getUint32(offset,true),type=view.getUint32(offset+4,true);
    if(type!==BIN_MAGIC || len%4 || offset+8+len!==bytes.length)
      throw Error("Unsupported or malformed GLB BIN chunk");
    bin=bytes.slice(offset+8,offset+8+len);
  }
  if(!Array.isArray(json.buffers) || json.buffers.length!==1 || json.buffers[0].uri)
    throw Error("Requires a GLB with one embedded binary buffer");
  const used=json.buffers[0].byteLength;
  if(!Number.isInteger(used)||used<0||used>bin.length)throw Error("GLB buffer length mismatch");
  for(const name of ["scenes","nodes","meshes","bufferViews","accessors"])
    if(!Array.isArray(json[name]))throw Error("Missing glTF "+name);
  if(!json.scenes.length)throw Error("GLB has no scene");
  return {json,bin:bin.slice(0,used),used};
}
function encodeGLB(json,bin,used) {
  const text=enc.encode(JSON.stringify(json)), jlen=align4(text.length),blen=align4(used);
  const length=12+8+jlen+8+blen;
  if(length>MAX_FILE || jlen>MAX_JSON)throw Error("Edited GLB exceeds safe 80 MB file limit");
  const bytes=new Uint8Array(length),v=new DataView(bytes.buffer);
  v.setUint32(0,HEADER_MAGIC,true);v.setUint32(4,2,true);v.setUint32(8,length,true);
  v.setUint32(12,jlen,true);v.setUint32(16,JSON_MAGIC,true);
  bytes.fill(32,20,20+jlen);bytes.set(text,20);
  const off=20+jlen;
  v.setUint32(off,blen,true);v.setUint32(off+4,BIN_MAGIC,true);
  bytes.set(bin.subarray(0,used),off+8);
  return bytes;
}
function partList(json) {
  const scene=json.scenes[json.scene??0];
  if(!scene)throw Error("Active glTF scene is missing");
  const animated=new Set();
  for(const animation of json.animations||[])
    for(const ch of animation.channels||[])
      if(Number.isInteger(ch.target?.node))animated.add(ch.target.node);
  const result=[],visited=new Set();
  function visit(id,depth) {
    if(depth>64||!Number.isInteger(id)||id<0||id>=json.nodes.length||visited.has(id))return;
    visited.add(id);
    const n=json.nodes[id];
    if(Number.isInteger(n.mesh) && n.mesh>=0 && n.mesh<json.meshes.length &&
       n.skin===undefined && !animated.has(id) && !n.extensions?.EXT_mesh_gpu_instancing) {
      const m=json.meshes[n.mesh];
      result.push({node:id,mesh:n.mesh,name:String(n.name||m.name||"Mesh part "+(id+1)).slice(0,120)});
    }
    for(const child of n.children||[])visit(child,depth+1);
  }
  for(const root of scene.nodes||[])visit(root,0);
  return result.slice(0,3000);
}
function getPrimitive(json,node,primitive){
  const part=partList(json).find(p=>p.node===node);
  if(!part)throw Error("Select a safe unanimated mesh part");
  const mesh=json.meshes[part.mesh];
  if(mesh.weights)throw Error("Morph target meshes require preprocessing");
  const prim=mesh.primitives?.[primitive];
  if(!prim)throw Error("Selected mesh surface doesn't exist");
  if((prim.mode??4)!==4)throw Error("Only ordinary triangle meshes can be sculpted");
  if(prim.targets?.length)throw Error("Morph targets cannot be sculpted yet");
  if(prim.extensions?.KHR_draco_mesh_compression)throw Error("Decode Draco geometry in Blender first");
  if(prim.attributes?.TANGENT!==undefined)
    throw Error("Tangent-space surface requires tangent recalculation; no edit applied");
  return {part,mesh,prim};
}
function attrView(json,id,used,components) {
  const accessor=json.accessors[id];
  if(!accessor || accessor.componentType!==5126 ||
     accessor.type!==(components===3?"VEC3":"SCALAR") ||
     accessor.sparse || accessor.normalized)
    throw Error("Surface needs ordinary float32, non-sparse positions");
  const count=accessor.count;
  if(!Number.isInteger(count)||count<1||count>150000)throw Error("Surface vertex limit exceeded");
  const view=json.bufferViews[accessor.bufferView];
  if(!view || (view.buffer!==0 && view.buffer!==undefined) || view.extensions)
    throw Error("Compressed or external geometry buffers are unsupported");
  if(!Number.isSafeInteger(view.byteLength) || view.byteLength<0 ||
     !Number.isSafeInteger(view.byteOffset??0) || !Number.isSafeInteger(accessor.byteOffset??0))
    throw Error("Invalid geometry buffer length or offset");
  const stride=view.byteStride??components*4;
  const offset=(view.byteOffset??0)+(accessor.byteOffset??0);
  const size=components*4;
  if(!Number.isSafeInteger(offset)||!Number.isSafeInteger(stride)||
      stride<size||stride>256||stride%4||offset%4||
      offset<0||offset+(count-1)*stride+size>(view.byteOffset??0)+view.byteLength||
      offset+(count-1)*stride+size>used)
    throw Error("Vertex buffer exceeds declared bounds");
  return {count,stride,offset};
}
function readSurface(json,bin,used,node,primitive){
  const {part,prim}=getPrimitive(json,node,primitive);
  const positionsId=prim.attributes?.POSITION;
  if(!Number.isInteger(positionsId))throw Error("Surface has no position attribute");
  const a=attrView(json,positionsId,used,3);
  const v=new DataView(bin.buffer,bin.byteOffset,bin.byteLength);
  const positions=new Float32Array(a.count*3);
  for(let i=0;i<a.count;i++)for(let j=0;j<3;j++){
    const n=v.getFloat32(a.offset+i*a.stride+j*4,true);
    if(!Number.isFinite(n)||Math.abs(n)>100000)throw Error("Invalid vertex coordinate");
    positions[i*3+j]=n;
  }
  let indices;
  if(prim.indices===undefined){
    if(a.count%3)throw Error("Nonindexed surface must contain triangle triplets");
    indices=Array.from({length:a.count},(_,i)=>i);
  }else{
    const ix=json.accessors[prim.indices];
    const width={5121:1,5123:2,5125:4}[ix?.componentType];
    if(!ix||!width||ix.type!=="SCALAR"||ix.sparse||!Number.isInteger(ix.count)||
       ix.count<3||ix.count%3||ix.count>900000)
      throw Error("Invalid indexed triangle accessor");
    const bv=json.bufferViews[ix.bufferView];
    const stride=bv?.byteStride??width;
    const offset=(bv?.byteOffset??0)+(ix.byteOffset??0);
    if(!bv||bv.buffer!==0&&bv.buffer!==undefined||bv.extensions||
       stride<width||stride>256||!Number.isSafeInteger(offset)||offset<0||
       offset%width||offset+(ix.count-1)*stride+width>(bv.byteOffset??0)+bv.byteLength||
       offset+(ix.count-1)*stride+width>used)
      throw Error("Triangle indices out of bounds");
    indices=Array.from({length:ix.count},(_,i)=>{
      const at=offset+i*stride;
      const n=width===1?v.getUint8(at):width===2?v.getUint16(at,true):v.getUint32(at,true);
      if(n>=a.count)throw Error("Triangle references a missing vertex");
      return n;
    });
  }
  const min=[Infinity,Infinity,Infinity],max=[-Infinity,-Infinity,-Infinity];
  for(let i=0;i<positions.length;i+=3)for(let axis=0;axis<3;axis++){
    const n=positions[i+axis];min[axis]=Math.min(min[axis],n);
    max[axis]=Math.max(max[axis],n);
  }
  return {part,prim,positions,indices,min,max};
}
function editedGeometry(json,bin,used,node,brush) {
  const {primitive,center,radius,delta}=brush;
  requireRange(primitive,0,10000,"Primitive");
  const c=validateArray(center,3,"Brush center"),d=validateArray(delta,3,"Brush direction");
  finite(radius,"Brush radius");
  if(radius<.001||radius>1000)throw Error("Brush radius must be 0.001–1000 local units");
  if(d.every(x=>x===0)||d.some(x=>Math.abs(x)>100)||c.some(x=>Math.abs(x)>10000))
    throw Error("Brush movement must be nonzero and each axis within 100 units");
  const next=clone(json);
  const {part,positions,indices}=readSurface(next,bin,used,node,primitive);
  const r2=radius*radius;
  let affected=0;
  for(let i=0;i<positions.length;i+=3){
    const dist=(positions[i]-c[0])**2+(positions[i+1]-c[1])**2+(positions[i+2]-c[2])**2;
    if(dist>=r2)continue;
    const t=Math.sqrt(Math.max(0,dist))/radius;
    const influence=1-t*t*(3-2*t);
    if(influence<=0)continue;
    for(let k=0;k<3;k++) {
      const coord=positions[i+k]+d[k]*influence;
      if(!Number.isFinite(coord))throw Error("Vertex coordinate overflow");
      positions[i+k]=coord;
    }
    affected++;
  }
  if(!affected)throw Error("No vertices under brush. Tap a vertex or increase brush radius.");
  let meshId=part.mesh;
  // When another node uses the same mesh, copy its primitive table.
  if(next.nodes.filter(n=>n.mesh===meshId).length>1) {
    meshId=next.meshes.length;next.meshes.push(clone(next.meshes[part.mesh]));
    next.nodes[node].mesh=meshId;
  }
  const normals=new Float32Array(positions.length);
  let valid=0;
  for(let i=0;i<indices.length;i+=3) {
    const a=indices[i]*3,b=indices[i+1]*3,c0=indices[i+2]*3;
    const ab=[0,1,2].map(k=>positions[b+k]-positions[a+k]);
    const ac=[0,1,2].map(k=>positions[c0+k]-positions[a+k]);
    const n=[ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]];
    if(!n.every(Number.isFinite)||n[0]*n[0]+n[1]*n[1]+n[2]*n[2]<1e-16)continue;
    valid++;
    for(const off of [a,b,c0])for(let k=0;k<3;k++)normals[off+k]+=n[k];
  }
  if(!valid)throw Error("No nondegenerate triangles to regenerate normals");
  for(let i=0;i<normals.length;i+=3){
    const len=Math.hypot(normals[i],normals[i+1],normals[i+2]);
    if(len>1e-8){for(let k=0;k<3;k++)normals[i+k]/=len;}
    else{normals[i]=0;normals[i+1]=1;normals[i+2]=0;}
  }
  const boundsMin=[Infinity,Infinity,Infinity],boundsMax=[-Infinity,-Infinity,-Infinity];
  for(let i=0;i<positions.length;i+=3)for(let k=0;k<3;k++){
    boundsMin[k]=Math.min(boundsMin[k],positions[i+k]);
    boundsMax[k]=Math.max(boundsMax[k],positions[i+k]);
  }
  const start=align4(used),length=start+positions.byteLength+normals.byteLength;
  if(length>MAX_FILE-100000)throw Error("Model exceeds safe binary editing budget");
  const updated=new Uint8Array(length);updated.set(bin.subarray(0,used));
  const w=new DataView(updated.buffer);
  let off=start;
  for(const arr of [positions,normals]){
    for(const x of arr){w.setFloat32(off,x,true);off+=4;}
  }
  const baseView=next.bufferViews.length;
  next.bufferViews.push({buffer:0,byteOffset:start,byteLength:positions.byteLength,target:34962});
  next.bufferViews.push({buffer:0,byteOffset:start+positions.byteLength,byteLength:normals.byteLength,target:34962});
  const posId=next.accessors.length;
  next.accessors.push({bufferView:baseView,componentType:5126,count:positions.length/3,
    type:"VEC3",min:boundsMin,max:boundsMax});
  next.accessors.push({bufferView:baseView+1,componentType:5126,count:normals.length/3,type:"VEC3"});
  Object.assign(next.meshes[meshId].primitives[primitive].attributes,{POSITION:posId,NORMAL:posId+1});
  next.buffers[0].byteLength=length;
  return {json:next,bin:updated,used:length,affected};
}
function editedTransform(json,bin,used,node,edit) {
  const next=clone(json);
  const part=partList(next).find(p=>p.node===node);
  if(!part)throw Error("Mesh node is not a safe editable component");
  const move=validateArray(edit.move,3,"Move"),angles=validateArray(edit.turn,3,"Rotation");
  const sizes=validateArray(edit.scale,3,"Scale");
  if(move.concat(angles).some(n=>Math.abs(n)>1000)||sizes.some(n=>n<.1||n>5))
    throw Error("Movement or scale exceeds safe editing range");
  const primCount=partList(next).find(p=>p.node===node);
  if(!primCount)throw Error("Mesh part is not safely editable");
  const mesh=next.meshes[primCount.mesh];
  const min=[Infinity,Infinity,Infinity],max=[-Infinity,-Infinity,-Infinity];
  for(let p=0;p<(mesh.primitives?.length||0);p++) {
    try{
      const surface=readSurface(next,bin,used,node,p);
      for(let k=0;k<3;k++){min[k]=Math.min(min[k],surface.min[k]);max[k]=Math.max(max[k],surface.max[k]);}
    }catch(e){/* safe pivot fallback for noneditable primitives */}
  }
  const pivot=min.map((n,i)=>Number.isFinite(n)?(n+max[i])/2:0);
  const n=next.nodes[node];
  const change=multiply(multiply(translation(...pivot),multiply(rotation(...angles),scaling(...sizes))),
    translation(...pivot.map(x=>-x)));
  const transformed=multiply(multiply(translation(...move),currentMatrix(n)),change);
  if(!transformed.every(Number.isFinite))throw Error("Invalid resulting transform");
  delete n.translation;delete n.rotation;delete n.scale;n.matrix=transformed;
  return {json:next,bin,used};
}

function insertedLibraryPart(destination,libraryBytes,name) {
  const label=String(name||"Library part").trim();
  if(!label||label.length>80)throw Error("Library part name must be 1 to 80 characters");
  const source=parseGLB(libraryBytes),parts=partList(source.json);
  if(parts.length!==1)throw Error("Library asset must contain exactly one mesh part");
  const {prim,positions,indices,min,max}=readSurface(
    source.json,source.bin,source.used,parts[0].node,0
  );
  if(source.json.meshes[parts[0].mesh].primitives.length!==1)
    throw Error("Complex multi-surface library assets require a Blender import");
  if(indices.length>100000)throw Error("Library part exceeds 100,000 triangles indices");
  const originalMaterial=source.json.materials?.[prim.material]||{};
  if(originalMaterial.extensions || originalMaterial.normalTexture ||
     originalMaterial.occlusionTexture || originalMaterial.emissiveTexture ||
     originalMaterial.pbrMetallicRoughness?.baseColorTexture ||
     originalMaterial.pbrMetallicRoughness?.metallicRoughnessTexture)
    throw Error("Textured library asset requires full glTF resource transfer; not applied");
  const color=originalMaterial.pbrMetallicRoughness?.baseColorFactor||[.4,.6,.7,1];
  validateArray(color,4,"Library part color");
  const geometry=new Float32Array(indices.length*3);
  const normals=new Float32Array(indices.length*3);
  // Expand indexed source to independent triangle vertices; retain exact shape.
  // The CC0 Forge Basics pack stores ordinary interleaved vertex/normals.
  let sourceNormals=null;
  if(Number.isInteger(prim.attributes?.NORMAL)){
    const av=attrView(source.json,prim.attributes.NORMAL,source.used,3);
    if(av.count!==positions.length/3)throw Error("Invalid library normal count");
    const rd=new DataView(source.bin.buffer,source.bin.byteOffset,source.bin.byteLength);
    sourceNormals=new Float32Array(positions.length);
    for(let i=0;i<av.count;i++)for(let k=0;k<3;k++){
      const value=rd.getFloat32(av.offset+i*av.stride+k*4,true);
      if(!Number.isFinite(value))throw Error("Non-finite library vertex normal");
      sourceNormals[i*3+k]=value;
    }
  }
  for(let i=0;i<indices.length;i+=3) {
    const a=indices[i],b=indices[i+1],c=indices[i+2];
    const ai=a*3,bi=b*3,ci=c*3;
    const ab=[0,1,2].map(k=>positions[bi+k]-positions[ai+k]);
    const ac=[0,1,2].map(k=>positions[ci+k]-positions[ai+k]);
    const cross=[ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]];
    const length=Math.hypot(...cross);
    const fallback=length>1e-8?cross.map(x=>x/length):[0,1,0];
    for(let vertex=0;vertex<3;vertex++) {
      const from=indices[i+vertex]*3,at=(i+vertex)*3;
      for(let k=0;k<3;k++) {
        geometry[at+k]=positions[from+k];
        normals[at+k]=sourceNormals?sourceNormals[from+k]:fallback[k];
      }
    }
  }
  const root=clone(destination.json),padded=align4(destination.used);
  const length=padded+geometry.byteLength+normals.byteLength;
  if(length>MAX_FILE-100000)throw Error("Adding this part would exceed the 80 MB GLB limit");
  const binary=new Uint8Array(length);
  binary.set(destination.bin.subarray(0,destination.used));
  const writer=new DataView(binary.buffer);
  let offset=padded;
  for(const arr of [geometry,normals])for(const x of arr){
    writer.setFloat32(offset,x,true);offset+=4;
  }
  const posView=root.bufferViews.length;
  root.bufferViews.push({buffer:0,byteOffset:padded,byteLength:geometry.byteLength,target:34962});
  root.bufferViews.push({buffer:0,byteOffset:padded+geometry.byteLength,byteLength:normals.byteLength,target:34962});
  const posAccessor=root.accessors.length;
  root.accessors.push({bufferView:posView,componentType:5126,count:indices.length,type:"VEC3",min,max});
  root.accessors.push({bufferView:posView+1,componentType:5126,count:indices.length,type:"VEC3"});
  root.materials ||= [];
  const matId=root.materials.length;
  root.materials.push({
    name:"Model Forge | "+label,doubleSided:originalMaterial.doubleSided??true,
    pbrMetallicRoughness:{
      baseColorFactor:color,
      metallicFactor:originalMaterial.pbrMetallicRoughness?.metallicFactor??.38,
      roughnessFactor:originalMaterial.pbrMetallicRoughness?.roughnessFactor??.45
    }
  });
  const meshId=root.meshes.length;
  root.meshes.push({name:label,primitives:[{attributes:{POSITION:posAccessor,NORMAL:posAccessor+1},
    material:matId,mode:4}]});
  const nodeId=root.nodes.length;
  root.nodes.push({name:label,mesh:meshId});
  const scene=root.scenes[root.scene??0];
  scene.nodes ||= [];scene.nodes.push(nodeId);
  root.buffers[0].byteLength=length;
  return {json:root,bin:binary,used:length,node:nodeId};
}

export class GlbWorkshop {
  constructor(source) {
    const {json,bin,used}=parseGLB(source);
    this.json=json;this.bin=bin;this.used=used;
    this.undoStack=[];this.redoStack=[];this.revision=1;
    if(!this.parts().length)throw Error("No safe, independent mesh components found");
  }
  parts(){return partList(this.json);}
  surfaces(node) {
    const part=this.parts().find(p=>p.node===node);
    if(!part)throw Error("Choose a mesh component first");
    return (this.json.meshes[part.mesh].primitives||[]).map((p,index)=>{
      try{
        const s=readSurface(this.json,this.bin,this.used,node,index);
        return {primitive:index,vertices:s.positions.length/3,min:s.min,max:s.max,editable:true};
      }catch(e){return {primitive:index,editable:false,reason:e.message};}
    });
  }
  getBytes(){return encodeGLB(this.json,this.bin,this.used);}
  getStats(){return {partCount:this.parts().length,revision:this.revision,bytes:this.getBytes().length};}
  previewTransform(node,delta){const v=editedTransform(this.json,this.bin,this.used,node,delta);
    return encodeGLB(v.json,v.bin,v.used);}
  previewBrush(node,brush){const v=editedGeometry(this.json,this.bin,this.used,node,brush);
    return {bytes:encodeGLB(v.json,v.bin,v.used),affected:v.affected};}
  applyTransform(node,delta){return this.commit(editedTransform(this.json,this.bin,this.used,node,delta));}
  applyBrush(node,brush){return this.commit(editedGeometry(this.json,this.bin,this.used,node,brush));}
  previewLibraryPart(sourceBytes,name){
    const next=insertedLibraryPart(this,sourceBytes,name);
    return {bytes:encodeGLB(next.json,next.bin,next.used),node:next.node};
  }
  addLibraryPart(sourceBytes,name){
    const next=insertedLibraryPart(this,sourceBytes,name);
    const bytes=this.commit(next);
    return {bytes,node:next.node};
  }
  commit(next){
    // Complete encoding before modifying in-memory state.
    const output=encodeGLB(next.json,next.bin,next.used);
    this.undoStack.push({json:this.json,used:this.used});
    if(this.undoStack.length>16)this.undoStack.shift();
    this.redoStack=[];this.json=next.json;this.bin=next.bin;this.used=next.used;
    this.revision++;return output;
  }
  undo(){
    if(!this.undoStack.length)return null;
    this.redoStack.push({json:this.json,used:this.used});
    const p=this.undoStack.pop();
    this.json=p.json;this.used=p.used;this.revision++;return this.getBytes();
  }
  redo(){
    if(!this.redoStack.length)return null;
    this.undoStack.push({json:this.json,used:this.used});
    const p=this.redoStack.pop();
    this.json=p.json;this.used=p.used;this.revision++;return this.getBytes();
  }
  get canUndo(){return this.undoStack.length>0;}
  get canRedo(){return this.redoStack.length>0;}
}
