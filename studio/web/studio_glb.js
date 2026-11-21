import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { GlbWorkshop } from "./glb_workshop.mjs";

/**
 * Browser imported-GLB editor, with native Android workshop feature parity:
 * - component search/selection by glTF node
 * - move/rotate/resize previews
 * - local vertex deformation with smooth falloff, normals, undo and GLB export
 * - direct tap-to-select and tap-to-place the deformation brush center
 *
 * Imported GLBs remain GLBs. They are not rewritten into MFORGE_DOC 5 because
 * the native parametric format currently has only one procedural 6-face object.
 */
export function initImportedWorkbench({scene,camera,orbit,renderer,el,notify,download,getShape}) {
  const loader=new GLTFLoader();
  const raycaster=new THREE.Raycaster(), mouse=new THREE.Vector2();
  let workshop=null,original=null,name="Imported-Car",root=null,parsed=null,selectedNode=null;
  let selectedMesh=null,highlight=null,brushMarker=null,staged=null;
  let tool="transform",pickPoint=false,active=false,dirty=false,wire=false,renderId=0;
  let lastSurface=null;
  const detail=(s,error=false)=>{el("glb-action-status").textContent=s;notify(s,error);};
  const read=id=>{
    const value=el(id).value;
    if(!value.trim()||!Number.isFinite(Number(value)))throw Error("Enter a finite number for "+id);
    return Number(value);
  };
  const setValues=(ids,values)=>{
    ids.forEach((id,i)=>{el(id).value=Number(values[i].toFixed(4));});
  };
  function disposeObject(object) {
    if(!object)return;
    object.traverse(node=>{
      if(node.geometry)node.geometry.dispose();
      if(node.material){
        const materials=Array.isArray(node.material)?node.material:[node.material];
        for(const mat of materials){
          for(const value of Object.values(mat)){
            if(value?.isTexture)value.dispose();
          }
          mat.dispose();
        }
      }
    });
  }
  function removeHighlight(){
    if(highlight){scene.remove(highlight);highlight.geometry.dispose();highlight.material.dispose();highlight=null;}
    if(brushMarker){brushMarker.parent?.remove(brushMarker);brushMarker.geometry.dispose();brushMarker.material.dispose();brushMarker=null;}
  }
  function partObject(nodeId) {
    if(!root||!parsed)return null;
    let found=null;
    root.traverse(obj=>{
      if(!found && parsed.parser.associations.get(obj)?.nodes===nodeId)found=obj;
    });
    return found;
  }
  function updateHighlight(){
    removeHighlight();
    const obj=partObject(selectedNode);
    if(!obj)return;
    highlight=new THREE.BoxHelper(obj,0x5ff2d9);
    highlight.raycast=()=>{};
    scene.add(highlight);
  }
  function styleWireframe(value){
    wire=value;
    if(!root)return;
    root.traverse(obj=>{
      if(!obj.isMesh)return;
      for(const mat of (Array.isArray(obj.material)?obj.material:[obj.material])){
        if(mat && "wireframe" in mat){mat.wireframe=wire;mat.needsUpdate=true;}
      }
    });
  }
  function updateStats(){
    if(!workshop)return;
    const data=workshop.getStats();
    el("model-title").textContent=name;
    el("revision").textContent="GLB edit "+data.revision;
    el("mesh-stats").textContent=data.partCount+" components · "+(data.bytes/1048576).toFixed(2)+" MB";
    el("glb-part-stats").textContent=(selectedNode===null?"No selected part":workshop.parts().find(p=>p.node===selectedNode)?.name||"Unknown part")
      +" · "+data.partCount+" total components · "+(dirty?"UNSAVED CHANGES":"saved state");
    el("glb-undo").disabled=!workshop.canUndo;
    el("glb-redo").disabled=!workshop.canRedo;
  }
  async function display(bytes,{fit=false}={}) {
    const serial=++renderId;
    const source=bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength);
    const gltf=await new Promise((resolve,reject)=>loader.parse(source,"",resolve,reject));
    if(serial!==renderId){disposeObject(gltf.scene);return;}
    const previous=root;
    removeHighlight();
    if(previous)scene.remove(previous);
    root=gltf.scene;parsed=gltf;
    if(active)scene.add(root);
    styleWireframe(wire);
    updateHighlight();
    if(fit)fitScene();
    disposeObject(previous);
  }
  function fitScene(){
    if(!root)return;
    const b=new THREE.Box3().setFromObject(root);
    if(b.isEmpty())return;
    const center=b.getCenter(new THREE.Vector3()), size=b.getSize(new THREE.Vector3());
    const diameter=Math.max(.1,size.length());
    const distance=diameter/(2*Math.tan(THREE.MathUtils.degToRad(camera.fov/2)))*.8;
    orbit.target.copy(center);
    camera.position.copy(center).add(new THREE.Vector3(.65*distance,.42*distance,distance));
    camera.near=Math.max(.0001,diameter/10000);
    camera.far=Math.max(500,diameter*150);
    camera.updateProjectionMatrix();orbit.update();
  }
  function cameraPreset(angle){
    if(!root)return;
    const bounds=new THREE.Box3().setFromObject(root);
    if(bounds.isEmpty())return;
    const center=bounds.getCenter(new THREE.Vector3());
    const distance=Math.max(.1,bounds.getSize(new THREE.Vector3()).length()*1.5);
    const map={front:[0,.05,1],side:[1,.05,0],top:[0,1,.0001],"three-quarter":[.7,.5,1]};
    const dir=map[angle];if(!dir)return;
    orbit.target.copy(center);
    camera.position.copy(center).add(new THREE.Vector3(...dir).normalize().multiplyScalar(distance));
    orbit.update();
  }
  function setTool(next){
    tool=next;pickPoint=false;
    el("glb-transform-tools").hidden=next!=="transform";
    el("glb-brush-tools").hidden=next!=="brush";
    el("glb-transform-mode").classList.toggle("active-tool",next==="transform");
    el("glb-brush-mode").classList.toggle("active-tool",next==="brush");
    el("glb-pick-point").classList.remove("active-tool");
    removeHighlight();updateHighlight();
    if(next==="brush")refreshSurfaceList();
    detail(next==="brush"?"Tap Pick point, then tap the car's surface to position your brush.":"Choose a transform and preview before applying.");
  }
  function enter(){
    if(!workshop)throw Error("Open a GLB first");
    active=true;
    const native=getShape();
    if(native)scene.remove(native);
    if(root)scene.add(root);
    el("body-panel").hidden=true;
    el("face-panel").hidden=true;
    el("glb-left-panel").hidden=false;
    el("glb-right-panel").hidden=false;
    el("face-pill").hidden=true;
    el("workspace-shape").classList.remove("active-workspace");
    document.querySelectorAll(".shape-nav").forEach(x=>x.hidden=true);
    el("viewport").querySelector(".help").textContent="DRAG TO ORBIT · TAP PART · PICK POINT TO BRUSH";
    updateStats();
  }
  function leave(){
    active=false;pickPoint=false;
    if(root)scene.remove(root);
    removeHighlight();
    const native=getShape();
    if(native)scene.add(native);
    el("body-panel").hidden=false;
    el("face-panel").hidden=false;
    el("glb-left-panel").hidden=true;
    el("glb-right-panel").hidden=true;
    el("face-pill").hidden=false;
    el("workspace-shape").classList.add("active-workspace");
    document.querySelectorAll(".shape-nav").forEach(x=>x.hidden=false);
    el("viewport").querySelector(".help").textContent="DRAG TO ORBIT · TAP TO SELECT NATIVE FACE";
  }
  function refreshPartList(){
    const sel=el("glb-part"),parts=workshop.parts();
    sel.replaceChildren();
    for(const p of parts){
      const option=document.createElement("option");
      option.value=String(p.node);
      option.textContent=p.name+" · node "+(p.node+1);
      sel.appendChild(option);
    }
    if(!parts.some(p=>p.node===selectedNode))selectedNode=parts[0]?.node??null;
    if(selectedNode!==null)sel.value=String(selectedNode);
    refreshSurfaceList();
    updateHighlight();updateStats();
  }
  function refreshSurfaceList(){
    if(!workshop||selectedNode===null)return;
    const surfaces=workshop.surfaces(selectedNode);
    const sel=el("glb-surface");sel.replaceChildren();
    for(const surf of surfaces){
      const op=document.createElement("option");
      op.value=String(surf.primitive);
      op.disabled=!surf.editable;
      op.textContent=surf.editable
        ?"Surface "+(surf.primitive+1)+" · "+surf.vertices+" vertices"
        :"Surface "+(surf.primitive+1)+" · locked: "+surf.reason;
      sel.appendChild(op);
    }
    const editable=surfaces.find(s=>s.editable);
    if(editable){
      sel.value=String(editable.primitive);
      lastSurface=editable;
      resetBrushCenter(editable);
    }else{
      lastSurface=null;
      detail("This part has no sculptable triangle surfaces. Transform it or convert unsupported geometry in Blender.",true);
    }
  }
  function resetBrushCenter(surf){
    const center=surf.min.map((v,i)=>(v+surf.max[i])/2);
    const extent=Math.max(...surf.min.map((v,i)=>surf.max[i]-v));
    setValues(["glb-center-x","glb-center-y","glb-center-z"],center);
    el("glb-radius").value=Number(Math.max(.01,extent*.35).toFixed(4));
  }
  function selectedSurface(){
    if(selectedNode===null)throw Error("Select a vehicle component first");
    const id=Number(el("glb-surface").value);
    const surface=workshop.surfaces(selectedNode).find(s=>s.primitive===id);
    if(!surface?.editable)throw Error(surface?.reason||"No editable surface selected");
    return surface;
  }
  function resetPreview(){
    staged=null;el("glb-preview-actions").hidden=true;
  }
  function setSelectedPart(node){
    if(!workshop.parts().some(p=>p.node===node))return;
    selectedNode=node;
    el("glb-part").value=String(node);
    resetPreview();refreshSurfaceList();updateHighlight();updateStats();
    detail("Selected "+workshop.parts().find(p=>p.node===node).name);
  }
  function nodeFromHit(object){
    for(let current=object;current&&current!==scene;current=current.parent){
      const id=parsed?.parser.associations.get(current)?.nodes;
      if(Number.isInteger(id) && workshop.parts().some(p=>p.node===id))return id;
    }
    return null;
  }
  function showBrushMarker(object,center){
    if(brushMarker){brushMarker.parent?.remove(brushMarker);brushMarker.geometry.dispose();brushMarker.material.dispose();}
    brushMarker=new THREE.Mesh(new THREE.SphereGeometry(1,16,12),
      new THREE.MeshBasicMaterial({color:0x43f4d3,wireframe:true,depthTest:false,transparent:true,opacity:.7}));
    brushMarker.raycast=()=>{};
    brushMarker.position.copy(center);
    const radius=Math.max(.001,Number(el("glb-radius").value)||.1);
    brushMarker.scale.setScalar(radius);
    object.add(brushMarker);
  }
  function handleViewportTap(event){
    if(!active||!root||!workshop)return false;
    const b=renderer.domElement.getBoundingClientRect();
    if(!b.width||!b.height)return true;
    mouse.set((event.clientX-b.left)/b.width*2-1,-(event.clientY-b.top)/b.height*2+1);
    raycaster.setFromCamera(mouse,camera);
    const hits=raycaster.intersectObject(root,true).filter(h=>h.object.isMesh);
    if(!hits.length)return true;
    const hit=hits[0],node=nodeFromHit(hit.object);
    if(node===null){detail("This surface is not an independently editable component.",true);return true;}
    if(node!==selectedNode)setSelectedPart(node);
    if(tool==="brush"&&pickPoint){
      const primitive=parsed.parser.associations.get(hit.object)?.primitives;
      if(Number.isInteger(primitive)){
        const option=Array.from(el("glb-surface").options).find(x=>Number(x.value)===primitive&&!x.disabled);
        if(option)el("glb-surface").value=String(primitive);
      }
      if(!selectedSurface())return true;
      const local=hit.object.worldToLocal(hit.point.clone());
      setValues(["glb-center-x","glb-center-y","glb-center-z"],[local.x,local.y,local.z]);
      showBrushMarker(hit.object,local);
      pickPoint=false;el("glb-pick-point").classList.remove("active-tool");
      detail("Brush positioned on imported mesh. Choose radius and displacement, then preview.");
    }
    return true;
  }
  function transformArgs(){
    const vals=["glb-transform-x","glb-transform-y","glb-transform-z"].map(read);
    const kind=el("glb-transform-kind").value;
    const delta={move:[0,0,0],turn:[0,0,0],scale:[1,1,1]};
    if(!Object.hasOwn(delta,kind))throw Error("Unknown transform operation");
    delta[kind]=vals;
    return delta;
  }
  function brushArgs(){
    const surface=selectedSurface();
    return {primitive:surface.primitive,
      center:["glb-center-x","glb-center-y","glb-center-z"].map(read),
      radius:read("glb-radius"),
      delta:["glb-delta-x","glb-delta-y","glb-delta-z"].map(read)};
  }
  async function preview(kind){
    if(!workshop||selectedNode===null)throw Error("Import a GLB and select a part first");
    const args=kind==="brush"?brushArgs():transformArgs();
    const result=kind==="brush"?workshop.previewBrush(selectedNode,args):
      {bytes:workshop.previewTransform(selectedNode,args),affected:null};
    const task={kind,node:selectedNode,args,affected:result.affected};
    await display(result.bytes);
    staged=task;el("glb-preview-actions").hidden=false;
    detail(kind==="brush"?"Preview: "+result.affected+" vertex/vertices affected. Rotate and inspect.":"Transform preview. Rotate and inspect before applying.");
  }
  async function commit(){
    if(!staged)throw Error("Preview your change first");
    const task=staged;
    const bytes=task.kind==="brush"?workshop.applyBrush(task.node,task.args):
      workshop.applyTransform(task.node,task.args);
    dirty=true;resetPreview();
    await display(bytes);
    refreshPartList();
    detail("Applied real GLB "+(task.kind==="brush"?"vertex deformation":"component transform")+". Undo or save the full car.");
  }
  async function discard(){
    if(!workshop)return;
    resetPreview();await display(workshop.getBytes());updateStats();
    detail("Preview discarded; committed geometry restored.");
  }
  async function changeHistory(direction){
    if(!workshop)return;
    resetPreview();
    const bytes=direction==="undo"?workshop.undo():workshop.redo();
    if(!bytes){detail("No "+direction+" step available.");return;}
    dirty=true;await display(bytes);refreshPartList();
    detail(direction==="undo"?"Undo applied to imported GLB.":"Redo applied to imported GLB.");
  }
  async function openFile(file){
    if(file.size>80*1024*1024)throw Error("GLB exceeds safe 80 MB editing limit");
    if(dirty && !confirm("Replace this imported car? Save your edited GLB first if needed."))return;
    const bytes=new Uint8Array(await file.arrayBuffer());
    const candidate=new GlbWorkshop(bytes);
    // Keep the currently edited project intact until new GLB renders.
    const previous={workshop,original,name,selectedNode,dirty};
    workshop=candidate;original=bytes.slice();name=file.name.replace(/\.glb$/i,"").slice(0,60)||"Imported Car";
    selectedNode=candidate.parts()[0].node;dirty=false;resetPreview();
    try{
      await display(candidate.getBytes(),{fit:true});
      refreshPartList();setTool("transform");enter();
      detail("Opened "+candidate.parts().length+" editable car components. Tap a part to select.");
    }catch(err){
      workshop=previous.workshop;original=previous.original;name=previous.name;
      selectedNode=previous.selectedNode;dirty=previous.dirty;
      if(workshop)await display(workshop.getBytes());
      throw err;
    }
  }
  el("glb-open").addEventListener("change",async event=>{
    const file=event.target.files?.[0];if(!file)return;
    try{await openFile(file);}catch(e){detail("Unable to open imported GLB: "+(e.message||e),true);}
    finally{event.target.value="";}
  });
  el("workspace-shape").addEventListener("click",leave);
  el("glb-part").addEventListener("change",()=>setSelectedPart(Number(el("glb-part").value)));
  el("glb-transform-mode").addEventListener("click",()=>setTool("transform"));
  el("glb-brush-mode").addEventListener("click",()=>setTool("brush"));
  el("glb-surface").addEventListener("change",()=>{
    try{const surf=selectedSurface();lastSurface=surf;resetBrushCenter(surf);}
    catch(e){detail(e.message,true);}
  });
  el("glb-transform-kind").addEventListener("change",()=>{
    const isScale=el("glb-transform-kind").value==="scale";
    ["glb-transform-x","glb-transform-y","glb-transform-z"].forEach(x=>el(x).value=isScale?"1":"0");
  });
  el("glb-pick-point").addEventListener("click",()=>{
    pickPoint=!pickPoint;el("glb-pick-point").classList.toggle("active-tool",pickPoint);
    detail(pickPoint?"Tap an editable vehicle surface to place brush center.":"Brush placement cancelled.");
  });
  el("glb-center-default").addEventListener("click",()=>{
    try{resetBrushCenter(selectedSurface());detail("Brush reset to selected surface center.");}
    catch(e){detail(e.message,true);}
  });
  el("glb-radius").addEventListener("change",()=>{
    if(brushMarker)brushMarker.scale.setScalar(Math.max(.001,Number(el("glb-radius").value)||.1));
  });
  el("glb-preview-transform").addEventListener("click",async()=>{
    try{await preview("transform");}catch(e){detail(e.message||String(e),true);}
  });
  el("glb-preview-brush").addEventListener("click",async()=>{
    try{await preview("brush");}catch(e){detail(e.message||String(e),true);}
  });
  el("glb-apply").addEventListener("click",async()=>{
    try{await commit();}catch(e){detail(e.message||String(e),true);}
  });
  el("glb-discard").addEventListener("click",async()=>{
    try{await discard();}catch(e){detail(e.message||String(e),true);}
  });
  el("glb-undo").addEventListener("click",async()=>{
    try{await changeHistory("undo");}catch(e){detail(e.message||String(e),true);}
  });
  el("glb-redo").addEventListener("click",async()=>{
    try{await changeHistory("redo");}catch(e){detail(e.message||String(e),true);}
  });
  el("glb-save").addEventListener("click",()=>{
    try{
      if(!workshop)throw Error("No imported model loaded");
      if(staged)throw Error("Apply or cancel the current preview before exporting");
      const safeName=name.replace(/[^A-Za-z0-9_-]/g,"-")||"Edited-Car";
      download(safeName+"-ModelForge.glb",workshop.getBytes(),"model/gltf-binary");
      dirty=false;updateStats();detail("Full edited GLB saved; original materials and unrelated meshes preserved.");
    }catch(e){detail(e.message||String(e),true);}
  });
  el("glb-reset").addEventListener("click",async()=>{
    try{
      if(!original)return;
      if(!confirm("Discard all changes to this car and reopen your original GLB?"))return;
      workshop=new GlbWorkshop(original);selectedNode=workshop.parts()[0].node;
      dirty=false;resetPreview();await display(workshop.getBytes(),{fit:true});
      refreshPartList();detail("Imported original restored. No changes to the source file.");
    }catch(e){detail(e.message||String(e),true);}
  });
  setTool("transform");
  return {
    get active(){return active;},
    get dirty(){return dirty;},
    fit:fitScene,
    viewAngle:cameraPreset,
    handleViewportTap,
    wireframe:styleWireframe,
    leave,
    openFile,
    async reopen(){if(workshop){enter();await display(workshop.getBytes());}}
  };
}
