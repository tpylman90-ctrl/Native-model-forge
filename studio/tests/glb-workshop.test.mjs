import test from "node:test";
import assert from "node:assert/strict";
import {GlbWorkshop} from "../web/glb_workshop.mjs";

const te=new TextEncoder(),td=new TextDecoder();
function pack(root,bin){
  const json=te.encode(JSON.stringify(root)),jPad=(4-json.length%4)%4;
  const bPad=(4-bin.length%4)%4,full=new Uint8Array(12+8+json.length+jPad+8+bin.length+bPad);
  const v=new DataView(full.buffer),off=20+json.length+jPad;
  v.setUint32(0,0x46546c67,true);v.setUint32(4,2,true);v.setUint32(8,full.length,true);
  v.setUint32(12,json.length+jPad,true);v.setUint32(16,0x4e4f534a,true);
  full.fill(32,20,off);full.set(json,20);
  v.setUint32(off,bin.length+bPad,true);v.setUint32(off+4,0x004e4942,true);
  full.set(bin,off+8);return full;
}
function unpack(bytes){
  const v=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
  const jlen=v.getUint32(12,true),root=JSON.parse(td.decode(bytes.subarray(20,20+jlen)).trimEnd());
  const off=20+jlen;assert.equal(v.getUint32(off+4,true),0x004e4942);
  const bin=bytes.slice(off+8,off+8+v.getUint32(off,true));
  return {root,bin};
}
function fixture({shared=true,tangent=false}={}){
  const floats=[
    0,0,0,0,0,1,   1,0,0,0,0,1,   0,1,0,0,0,1
  ];
  const bin=new Uint8Array(72),view=new DataView(bin.buffer);
  floats.forEach((v,i)=>view.setFloat32(i*4,v,true));
  const root={
    asset:{version:"2.0",generator:"test fixture"},scene:0,
    scenes:[{nodes:shared?[0,1]:[0]}],
    nodes:shared?[{name:"Front fender",mesh:0},{name:"Duplicate fender",mesh:0}]:
      [{name:"Front fender",mesh:0}],
    meshes:[{name:"Body part",primitives:[{attributes:{POSITION:0,NORMAL:1},material:0,mode:4}]}],
    buffers:[{byteLength:bin.length}],
    bufferViews:[{buffer:0,byteOffset:0,byteLength:72,byteStride:24,target:34962}],
    accessors:[
      {bufferView:0,componentType:5126,count:3,type:"VEC3",min:[0,0,0],max:[1,1,0]},
      {bufferView:0,byteOffset:12,componentType:5126,count:3,type:"VEC3"}
    ],
    materials:[{name:"Original premium paint",pbrMetallicRoughness:{metallicFactor:.8,roughnessFactor:.22}}],
    extensionsUsed:["KHR_materials_clearcoat"],extras:{factory:"preserve-original"}
  };
  if(tangent)root.meshes[0].primitives[0].attributes.TANGENT=1;
  return pack(root,bin);
}
function vertex(bytes,node,i){
  const {root,bin}=unpack(bytes);
  const mesh=root.meshes[root.nodes[node].mesh],pr=mesh.primitives[0];
  const accessor=root.accessors[pr.attributes.POSITION];
  const b=root.bufferViews[accessor.bufferView];
  const offset=(b.byteOffset||0)+(accessor.byteOffset||0)+i*(b.byteStride||12);
  const dv=new DataView(bin.buffer,bin.byteOffset,bin.length);
  return Array.from({length:3},(_,k)=>dv.getFloat32(offset+k*4,true));
}
function sourcePrefix(bytes){return unpack(bytes).bin;}

test("import native-compatible GLB, list component nodes, and preserve its metadata",()=>{
  const w=new GlbWorkshop(fixture());
  assert.deepEqual(w.parts().map(p=>p.name),["Front fender","Duplicate fender"]);
  assert.equal(w.surfaces(0)[0].vertices,3);
  assert.deepEqual(w.surfaces(0)[0].min,[0,0,0]);
  assert.equal(w.getStats().partCount,2);
  assert.equal(unpack(w.getBytes()).root.extras.factory,"preserve-original");
});
test("localized edit, preview/commit, shared instance isolation, GLB reopen, undo and redo",()=>{
  const input=fixture(),w=new GlbWorkshop(input);
  const brush={primitive:0,center:[0,0,0],radius:.3,delta:[0,0,.2]};
  const before=w.getBytes(),preview=w.previewBrush(0,brush);
  assert.equal(preview.affected,1);
  assert.deepEqual(w.getBytes(),before,"preview must not mutate the committed project");
  assert.ok(Math.abs(vertex(preview.bytes,0,0)[2]-.2)<1e-6);
  assert.equal(vertex(preview.bytes,0,1)[2],0);
  const output=w.applyBrush(0,brush);
  assert.deepEqual(output,preview.bytes);
  assert.equal(vertex(output,1,0)[2],0,"the second node sharing mesh must remain untouched");
  const {root}=unpack(output);
  assert.notEqual(root.nodes[0].mesh,root.nodes[1].mesh,"edited instance must clone its mesh");
  assert.deepEqual(root.materials,unpack(before).root.materials);
  assert.equal(root.extras.factory,"preserve-original");
  const originalBin=sourcePrefix(before),newBin=sourcePrefix(output);
  assert.deepEqual(newBin.slice(0,originalBin.length),originalBin);
  assert.ok(newBin.length>originalBin.length);
  assert.deepEqual(w.undo(),before);
  assert.deepEqual(w.redo(),output);
  assert.equal(vertex(new GlbWorkshop(output).getBytes(),0,0)[2],vertex(output,0,0)[2]);
});
test("smooth falloff only moves vertices within influence radius",()=>{
  const w=new GlbWorkshop(fixture({shared:false}));
  const edited=w.applyBrush(0,{primitive:0,center:[0,0,0],radius:1.5,delta:[0,0,.5]});
  const z0=vertex(edited,0,0)[2],z1=vertex(edited,0,1)[2],z2=vertex(edited,0,2)[2];
  assert.equal(z0,.5);
  assert.ok(z1>0 && z1<.5, "near vertex should get partial movement");
  assert.ok(Math.abs(z1-z2)<1e-6);
});
test("component transform changes only selected glTF node, not original bytes or other nodes",()=>{
  const w=new GlbWorkshop(fixture());
  const prior=w.getBytes(),before=unpack(prior);
  const delta={move:[.5,.1,0],turn:[0,45,0],scale:[1.2,1,1]};
  const preview=w.previewTransform(0,delta);
  assert.deepEqual(w.getBytes(),prior);
  const updated=w.applyTransform(0,delta);
  const doc=unpack(updated).root;
  assert.deepEqual(doc.nodes[1],before.root.nodes[1]);
  assert.equal(doc.nodes[0].matrix.length,16);
  assert.deepEqual(sourcePrefix(updated),sourcePrefix(prior));
  assert.deepEqual(preview,updated);
  assert.deepEqual(w.undo(),prior);
  assert.deepEqual(w.redo(),updated);
});
test("rejected edits are atomic and keep previous undo state",()=>{
  const w=new GlbWorkshop(fixture()),original=w.getBytes();
  for(const bad of [
    {primitive:0,center:[40,40,40],radius:.5,delta:[0,0,.1]},
    {primitive:0,center:[0,0,0],radius:-.5,delta:[0,0,.1]},
    {primitive:0,center:[0,0,0],radius:.5,delta:[0,0,0]},
    {primitive:0,center:[0,NaN,0],radius:.5,delta:[0,0,.1]},
    {primitive:5,center:[0,0,0],radius:.5,delta:[0,0,.1]}
  ]){
    assert.throws(()=>w.applyBrush(0,bad));
    assert.deepEqual(w.getBytes(),original);
    assert.equal(w.canUndo,false);
  }
  assert.throws(()=>w.applyTransform(0,{move:[0,0,0],turn:[0,0,0],scale:[-2,1,1]}));
});
test("unsupported tangent-space surfaces are rejected without model damage",()=>{
  const w=new GlbWorkshop(fixture({tangent:true})),original=w.getBytes();
  assert.equal(w.surfaces(0)[0].editable,false);
  assert.match(w.surfaces(0)[0].reason,/Tangent/);
  assert.throws(()=>w.applyBrush(0,{primitive:0,center:[0,0,0],radius:.5,delta:[0,0,.1]}));
  assert.deepEqual(w.getBytes(),original);
});
test("invalid file headers and byte limits reject safely",()=>{
  assert.throws(()=>new GlbWorkshop(new Uint8Array(12)),/28/);
  const altered=fixture();new DataView(altered.buffer).setUint32(8,999,true);
  assert.throws(()=>new GlbWorkshop(altered),/complete/);
});
