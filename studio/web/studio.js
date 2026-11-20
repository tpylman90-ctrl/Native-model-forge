import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import { GLTFExporter } from "three/addons/exporters/GLTFExporter.js";
import createForgeCore from "./forge_core.js";
import {encodeSession, decodeSession} from "./share_session.mjs";

const el = (id) => document.getElementById(id);
let core, model = null, activeFace = 4, state = null, modified = false, showWire = false;
const faceNames = ["Front +Z","Back −Z","Right +X","Left −X","Top +Y","Bottom −Y"];
const viewport = el("viewport"), status = el("status");
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x0b1423);
const camera = new THREE.PerspectiveCamera(45, 1, .001, 1000);
camera.position.set(8,6,12);
const renderer = new THREE.WebGLRenderer({antialias:true});
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
viewport.appendChild(renderer.domElement);
const orbit = new OrbitControls(camera,renderer.domElement);
orbit.enableDamping = true;orbit.minDistance = .2;orbit.maxDistance = 300;
scene.add(new THREE.HemisphereLight(0xd8edff,0x35445a,2));
const lamp = new THREE.DirectionalLight(0xffffff,3.1);
lamp.position.set(7,12,9);scene.add(lamp);
const rim = new THREE.DirectionalLight(0x62f6de,1.2);
rim.position.set(-8,6,-10);scene.add(rim);
const grid = new THREE.GridHelper(20,20,0x37506d,0x1a293c);
grid.position.y = -1.1;scene.add(grid);
function frame(){
  requestAnimationFrame(frame);
  orbit.update();renderer.render(scene,camera);
}
frame();
function resize(){
  const width=Math.max(1,viewport.clientWidth),height=Math.max(1,viewport.clientHeight);
  camera.aspect=width/height;camera.updateProjectionMatrix();
  renderer.setSize(width,height,false);
}
new ResizeObserver(resize).observe(viewport);resize();
function notify(message,isError=false){
  status.textContent=message;status.classList.toggle("error",isError);
}
function call(name,type="number",types=[],args=[]){
  return core.ccall(name,type,types,args);
}
function must(name,types,args){
  if(call(name,"number",types,args)!==1)
    throw new Error(call("mf_last_error","string") || "Native edit rejected");
}
function safe(action){
  try{action();}catch(e){notify(e.message||String(e),true);}
}
function num(id){
  if(el(id).value.trim()==="")throw new Error(id+" cannot be empty");
  const result=Number(el(id).value);
  if(!Number.isFinite(result))throw new Error(id+" must be a finite number");
  return result;
}
function fields(ids){return ids.map(num);}
function readState(){
  const raw=call("mf_state_json","string");
  if(!raw)throw new Error(call("mf_last_error","string"));
  return JSON.parse(raw);
}
function selectFace(face){
  if(!Number.isInteger(face)||face<0||face>5)return;
  activeFace=face;
  if(!state)return;
  el("face").value=String(face);
  el("face-pill").textContent=faceNames[face]+" selected";
  const r=state.faces[face];
  const inputs={kind:r.kind,regionWidth:r.width,regionHeight:r.height,
    offsetU:r.offset_u,offsetV:r.offset_v,depth:r.depth_m};
  for(const [key,value] of Object.entries(inputs))el(key).value=Number(value.toFixed(4));
  for(let i=0;i<4;i++)el("curve"+i).value=Number(r.curves[i].toFixed(4));
  if(model)model.material.forEach((mat,index)=>{
    mat.color.setHex(index===face?0x19cdbb:0x7890ad);
    mat.emissive.setHex(index===face?0x103b42:0x000000);
  });
}
function fit(){
  if(!model)return;
  const bounds=model.geometry.boundingBox;
  const center=bounds.getCenter(new THREE.Vector3());
  const diameter=Math.max(.1,bounds.getSize(new THREE.Vector3()).length());
  const distance=diameter/(2*Math.tan(THREE.MathUtils.degToRad(camera.fov/2)))*.78;
  orbit.target.copy(center);
  camera.position.copy(center).add(new THREE.Vector3(.75*distance,.5*distance,distance));
  camera.near=Math.max(.001,diameter/10000);camera.far=Math.max(300,diameter*100);
  camera.updateProjectionMatrix();orbit.update();
}
function refresh(shouldFit=false,isEdit=true){
  state=readState();
  const count=call("mf_vertex_count"),ptr=call("mf_vertex_data");
  if(count<1||count>500000||!ptr)throw new Error("Invalid native vertex buffer");
  // WebAssembly memory may grow. Never retain a native pointer after editing.
  const packed=core.HEAPF32.slice(ptr/4,ptr/4+count*6);
  const positions=new Float32Array(count*3),normals=new Float32Array(count*3);
  for(let i=0;i<count;i++){
    const a=i*6,b=i*3;
    positions[b]=packed[a];positions[b+1]=packed[a+1];positions[b+2]=packed[a+2];
    normals[b]=packed[a+3];normals[b+1]=packed[a+4];normals[b+2]=packed[a+5];
  }
  const geometry=new THREE.BufferGeometry();
  geometry.setAttribute("position",new THREE.BufferAttribute(positions,3));
  geometry.setAttribute("normal",new THREE.BufferAttribute(normals,3));
  let start=0;
  for(let face=0;face<6;face++){
    const size=call("mf_face_vertex_count","number",["number"],[face]);
    geometry.addGroup(start,size,face);start+=size;
  }
  if(start!==count)throw new Error("Native face mapping mismatch");
  geometry.computeBoundingBox();
  const materials=Array.from({length:6},()=>new THREE.MeshStandardMaterial({
    color:0x7890ad,roughness:.4,metalness:.5,side:THREE.DoubleSide,wireframe:showWire
  }));
  const next=new THREE.Mesh(geometry,materials);
  if(model){
    scene.remove(model);model.geometry.dispose();model.material.forEach(m=>m.dispose());
  }
  model=next;scene.add(next);
  ["width","height","length"].forEach((key,i)=>el(key).value=Number(state.size[i].toFixed(4)));
  ["topWidth","topLength","shift"].forEach((key,i)=>el(key).value=Number(state.profile[i].toFixed(4)));
  el("name").value=state.name;
  el("model-title").textContent=state.name;
  el("revision").textContent="Rev "+state.revision;
  el("mesh-stats").textContent=(count/3).toLocaleString()+" triangles";
  el("coop-source").textContent=state.name+" · revision "+state.revision+" · editable native .mforge";
  if(shouldFit)fit();
  selectFace(activeFace);
  if(isEdit)modified=true;
}
function edit(name,types,args,message){
  must(name,types,args);refresh();notify(message||"Native edit applied · undo available");
}
const raycaster=new THREE.Raycaster(),mouse=new THREE.Vector2();
let pointerDown=null;
renderer.domElement.addEventListener("pointerdown",e=>{
  pointerDown={x:e.clientX,y:e.clientY};
});
renderer.domElement.addEventListener("pointerup",e=>{
  if(!pointerDown||!model)return;
  const distance=Math.hypot(e.clientX-pointerDown.x,e.clientY-pointerDown.y);
  pointerDown=null;if(distance>7)return;
  const b=renderer.domElement.getBoundingClientRect();
  mouse.set((e.clientX-b.left)/b.width*2-1,-(e.clientY-b.top)/b.height*2+1);
  raycaster.setFromCamera(mouse,camera);
  const hits=raycaster.intersectObject(model,false);
  if(hits.length&&hits[0].face)selectFace(hits[0].face.materialIndex);
});
function viewAngle(angle){
  if(!model)return;
  const center=model.geometry.boundingBox.getCenter(new THREE.Vector3());
  const distance=Math.max(.5,model.geometry.boundingBox.getSize(new THREE.Vector3()).length()*1.8);
  const directions={
    "front":new THREE.Vector3(0,.12,1),
    "side":new THREE.Vector3(1,.14,0),
    "top":new THREE.Vector3(0,1,.0001),
    "three-quarter":new THREE.Vector3(.75,.5,1)
  };
  const dir=directions[angle];
  if(!dir)return;
  orbit.target.copy(center);
  camera.position.copy(center).add(dir.normalize().multiplyScalar(distance));
  orbit.update();
}
document.querySelectorAll("[data-angle]").forEach(button=>{
  button.addEventListener("click",()=>viewAngle(button.dataset.angle));
});

el("face").addEventListener("change",()=>selectFace(Number(el("face").value)));
el("fit").addEventListener("click",fit);
el("wire").addEventListener("click",()=>{
  showWire=!showWire;if(model)model.material.forEach(m=>{m.wireframe=showWire;m.needsUpdate=true;});
  el("wire").textContent=showWire?"Wireframe on":"Wireframe off";
});
el("new").addEventListener("click",()=>safe(()=>{
  if(modified&&!confirm("Replace this model? Save the .mforge file first if needed."))return;
  must("mf_new_box",["number","number","number"],[4.5,1.8,8.4]);
  activeFace=4;refresh(true,false);modified=false;notify("New native starter body created");
}));
el("reshape").addEventListener("click",()=>safe(()=>{
  edit("mf_reshape",Array(7).fill("number"),
    [...fields(["width","height","length","topWidth","topLength","shift"]),state.revision],
    "Body dimensions and profile saved");
}));
el("rename").addEventListener("click",()=>safe(()=>{
  edit("mf_rename",["string","number"],[el("name").value,state.revision],"Part renamed");
}));
function formRegion(){
  const kind=num("kind");
  return {face_index:activeFace,kind,width:num("regionWidth"),height:num("regionHeight"),
    offset_u:num("offsetU"),offset_v:num("offsetV"),
    depth_m:kind===-1?0:num("depth"),
    curves:kind===2?fields(["curve0","curve1","curve2","curve3"]):[0,0,0,0]};
}
function region(p){
  if(!Array.isArray(p.curves)||p.curves.length!==4)
    throw new Error("Four edge curve parameters required");
  const args=[p.face_index,p.kind,p.width,p.height,p.offset_u,p.offset_v,p.depth_m,...p.curves,state.revision];
  if(!args.every(Number.isFinite))throw new Error("Region contains a nonfinite value");
  edit("mf_set_region",Array(12).fill("number"),args,"Native curved region applied");
}
el("apply-region").addEventListener("click",()=>safe(()=>region(formRegion())));
el("clear-region").addEventListener("click",()=>safe(()=>{
  const p=formRegion();p.kind=-1;p.depth_m=0;p.curves=[0,0,0,0];region(p);
}));
el("mirror").addEventListener("click",()=>safe(()=>{
  const other=activeFace^1,overwrite=state.faces[other].kind>=0;
  if(overwrite&&!confirm("Replace the existing region on the opposite face?"))return;
  edit("mf_mirror",Array(3).fill("number"),[activeFace,overwrite?1:0,state.revision],
    "Region mirrored to opposite face");
}));
el("undo").addEventListener("click",()=>safe(()=>
  edit("mf_undo",["number"],[state.revision],"Undo complete")));
el("redo").addEventListener("click",()=>safe(()=>
  edit("mf_redo",["number"],[state.revision],"Redo complete")));
function stem(){
  return (state.name||"ModelForge").replace(/[^a-z0-9_-]/gi,"-").slice(0,55);
}
function download(name,data,mime){
  const url=URL.createObjectURL(new Blob([data],{type:mime}));
  const a=document.createElement("a");a.href=url;a.download=name;
  document.body.appendChild(a);a.click();a.remove();
  setTimeout(()=>URL.revokeObjectURL(url),2000);
}
el("save").addEventListener("click",()=>safe(()=>{
  download(stem()+".mforge",call("mf_save_project","string"),"text/plain;charset=utf-8");
  modified=false;notify("Editable .mforge saved for Android Model Forge");
}));
el("open").addEventListener("change",async e=>{
  const file=e.target.files&&e.target.files[0];if(!file)return;
  try{
    if(file.size>1024*1024)throw new Error("Project exceeds 1 MiB limit");
    const text=await file.text();
    if(modified&&!confirm("Replace model? Save your changes first if needed."))return;
    must("mf_load_project",["string"],[text]);
    activeFace=4;refresh(true,false);modified=false;
    notify("Editable native Model Forge project opened");
  }catch(err){notify(err.message||String(err),true);}
  finally{e.target.value="";}
});
el("export").addEventListener("click",()=>safe(()=>{
  if(!model)throw new Error("No model available");
  const exporter=new GLTFExporter();
  // Never bake temporary face selection tint or wireframe into the GLB.
  const cleanMaterials=Array.from({length:6},()=>new THREE.MeshStandardMaterial({
    color:0x8395aa,roughness:.42,metalness:.45,side:THREE.DoubleSide
  }));
  const cleanMesh=new THREE.Mesh(model.geometry,cleanMaterials);
  exporter.parse(cleanMesh,data=>{
    download(stem()+".glb",data,"model/gltf-binary");
    notify("GLB mesh exported; save .mforge separately for editing");
  },e=>notify(e.message||String(e),true),{binary:true});
}));

// Manual command interface. It is not a remote autonomous AI connection.
function finite(value,label){
  if(typeof value!=="number"||!Number.isFinite(value))
    throw new Error(label+" must be a finite number");
  return value;
}
function vector(value,n,label){
  if(!Array.isArray(value)||value.length!==n)
    throw new Error(label+" requires "+n+" numbers");
  return value.map(item=>finite(item,label));
}
el("template-command").addEventListener("click",()=>safe(()=>{
  const command={protocol:"model-forge/command/v1",origin:"assistant",requires_approval:true,
    object_id:state.id,base_revision:state.revision,
    operation:"set_face_region",parameters:formRegion()};
  el("command-json").value=JSON.stringify(command,null,2);
  notify("Editable command template inserted");
}));
function applyCommand(cmd){
  if(!cmd||cmd.protocol!=="model-forge/command/v1")
    throw new Error("Unrecognized command protocol");
  if(cmd.object_id!==state.id||cmd.base_revision!==state.revision)
    throw new Error("Stale revision or wrong object ID");
  const p=cmd.parameters;
  if(!p||typeof p!=="object"||Array.isArray(p))
    throw new Error("Invalid command parameters");
  if(cmd.operation==="reshape"){
    edit("mf_reshape",Array(7).fill("number"),
      [...vector(p.size_m,3,"size_m"),...vector(p.top_profile,3,"top_profile"),state.revision]);
  }else if(cmd.operation==="set_face_region"){
    const face=finite(p.face_index,"face_index"),kind=finite(p.kind,"kind");
    if(!Number.isInteger(face)||!Number.isInteger(kind))throw new Error("Face index and kind must be integers");
    region({face_index:face,kind,width:finite(p.width,"width"),
      height:finite(p.height,"height"),offset_u:finite(p.offset_u,"offset_u"),
      offset_v:finite(p.offset_v,"offset_v"),depth_m:finite(p.depth_m,"depth_m"),
      curves:vector(p.curves,4,"curves")});
  }else if(cmd.operation==="mirror_face_region"){
    const face=finite(p.face_index,"face_index");
    if(!Number.isInteger(face)||typeof p.overwrite!=="boolean")
      throw new Error("mirror requires integer face_index and boolean overwrite");
    edit("mf_mirror",Array(3).fill("number"),
      [face,p.overwrite?1:0,state.revision]);
  }else if(cmd.operation==="rename_object"){
    if(typeof p.name!=="string")throw new Error("Name must be text");
    edit("mf_rename",["string","number"],[p.name,state.revision]);
  }else throw new Error("Unsupported operation");
}
el("apply-command").addEventListener("click",()=>safe(()=>{
  const json=el("command-json").value;
  if(json.length>16384)throw new Error("Command exceeds 16 KiB");
  const cmd=JSON.parse(json);
  if(!cmd||cmd.protocol!=="model-forge/command/v1")
    throw new Error("Unsupported command");
  if(!confirm("Review native edit:\n"+cmd.operation+"\nObject "+cmd.object_id+
    " / base revision "+cmd.base_revision+"\n\nApprove this change?"))return;
  applyCommand(cmd);notify("Approved JSON command applied to native model");
}));
window.addEventListener("beforeunload",e=>{
  if(modified){e.preventDefault();e.returnValue="";}
});
// Shared baseline can be updated by the collaborator through GitHub.
// The browser fetches it on demand, without authentication or write tokens.
async function loadSharedModel(confirmReplacement=true){
  if(confirmReplacement && modified &&
     !confirm("Replace your local edits with the shared hull? Save or copy a co-op link first."))return;
  const response=await fetch("./sessions/hovercraft-hull.mforge?v="+Date.now(),{cache:"no-store"});
  if(!response.ok)throw new Error("Could not fetch shared hull: HTTP "+response.status);
  const text=await response.text();
  must("mf_load_project",["string"],[text]);
  activeFace=4;refresh(true,false);modified=false;
  if(location.hash.startsWith("#mf1="))
    history.replaceState(null,"",location.pathname+location.search);
  notify("Shared hovercraft loaded. Edit it, then copy your co-op link.");
}
el("shared-demo").addEventListener("click",async()=>{
  try{await loadSharedModel();}catch(e){notify(e.message||String(e),true);}
});
async function copyText(text){
  if(navigator.clipboard && navigator.clipboard.writeText){
    await navigator.clipboard.writeText(text);return;
  }
  const box=document.createElement("textarea");
  box.value=text;box.style.position="fixed";box.style.opacity="0";
  document.body.appendChild(box);box.focus();box.select();
  const copied=document.execCommand("copy");box.remove();
  if(!copied)window.prompt("Copy this model link:",text);
}
el("copy-share").addEventListener("click",async()=>{
  try{
    const project=call("mf_save_project","string");
    const url=new URL(window.location.href);
    url.hash=encodeSession(project);
    await copyText(url.toString());
    notify("Co-op link copied. Paste it into our ChatGPT conversation to share this exact editable model.");
  }catch(e){notify(e.message||String(e),true);}
});
el("screenshot").addEventListener("click",()=>safe(()=>{
  renderer.render(scene,camera);
  const image=renderer.domElement.toDataURL("image/png");
  const a=document.createElement("a");a.href=image;a.download=stem()+"-inspection.png";
  document.body.appendChild(a);a.click();a.remove();
  notify("Screenshot saved. Attach it in chat for visual evaluation.");
}));
try{
  core=await createForgeCore();
  const linked=decodeSession(window.location.hash);
  if(linked!==null){
    must("mf_load_project",["string"],[linked]);
    activeFace=4;refresh(true,false);modified=false;
    notify("Co-op link opened. Exact editable project restored.");
  }else{
    try{await loadSharedModel(false);}
    catch(e){
      must("mf_new_box",["number","number","number"],[4.5,1.8,8.4]);
      refresh(true,false);modified=false;
      notify("Shared model unavailable; a blank native body was created: "+(e.message||e),true);
    }
  }
  el("loading").remove();
}catch(e){
  // A bad sharing link is recoverable and does not replace a saved Android model.
  try{
    if(!core)throw e;
    must("mf_new_box",["number","number","number"],[4.5,1.8,8.4]);
    refresh(true,false);modified=false;el("loading").remove();
    notify("Link error: opened safe starter shape. "+(e.message||e),true);
  }catch(inner){
    el("loading").textContent="Studio failed to start: "+(inner.message||inner);
    notify(inner.message||String(inner),true);
  }
}
