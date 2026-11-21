/**
 * Reviewable human/AI command protocol for imported Model Forge GLBs.
 * Commands are data only. No JavaScript evaluation, network permissions or
 * arbitrary access to the user's filesystem or scene. User preview+approval
 * is required before applying a returned edit.
 */
const vec=(value,n,label)=>{
  if(!Array.isArray(value)||value.length!==n||
     value.some(x=>typeof x!=="number"||!Number.isFinite(x)))
    throw Error(label+" must be "+n+" finite numbers");
  return value;
};
export function templateGlbCommand(workshop,node,operation="transform",primitive=0) {
  if(!workshop.parts().some(p=>p.node===node))throw Error("Select an editable GLB node first");
  const command={
    protocol:"model-forge/glb-command/v1",
    origin:"assistant",
    requires_approval:true,
    node_id:node,
    base_revision:workshop.revision,
    operation
  };
  command.parameters=operation==="brush"?
    {primitive_index:primitive,center_local:[0,0,0],radius_local:.5,delta_local:[0,.1,0]}:
    {move_m:[0,0,0],turn_degrees:[0,0,0],scale:[1,1,1]};
  return command;
}
export function reviewGlbCommand(workshop,input){
  if(typeof input!=="string"||input.length>16384)throw Error("GLB command exceeds safe length limit");
  let cmd;
  try{cmd=JSON.parse(input);}catch(e){throw Error("Command is not valid JSON: "+e.message);}
  if(!cmd||typeof cmd!=="object"||Array.isArray(cmd)||
     cmd.protocol!=="model-forge/glb-command/v1"||
     cmd.requires_approval!==true)
    throw Error("Unsupported or unapproved GLB command protocol");
  if(!Number.isInteger(cmd.node_id)||!workshop.parts().some(p=>p.node===cmd.node_id))
    throw Error("Command targets a missing or protected mesh node");
  if(!Number.isInteger(cmd.base_revision)||cmd.base_revision!==workshop.revision)
    throw Error("Stale GLB command; rebase it on the current revision");
  const p=cmd.parameters;
  if(!p||typeof p!=="object"||Array.isArray(p))throw Error("Missing command parameters");
  if(cmd.operation==="transform"){
    const args={move:vec(p.move_m,3,"move_m"),
      turn:vec(p.turn_degrees,3,"turn_degrees"),
      scale:vec(p.scale,3,"scale")};
    const bytes=workshop.previewTransform(cmd.node_id,args);
    return {kind:"transform",node:cmd.node_id,args,bytes,description:"Preview part movement, rotation and scaling"};
  }
  if(cmd.operation==="brush"){
    if(!Number.isInteger(p.primitive_index))throw Error("Mesh primitive index must be an integer");
    if(typeof p.radius_local!=="number"||!Number.isFinite(p.radius_local))
      throw Error("Brush radius must be finite");
    const args={primitive:p.primitive_index,center:vec(p.center_local,3,"center_local"),
      radius:p.radius_local,delta:vec(p.delta_local,3,"delta_local")};
    const result=workshop.previewBrush(cmd.node_id,args);
    return {kind:"brush",node:cmd.node_id,args,bytes:result.bytes,affected:result.affected,
      description:"Preview "+result.affected+" influenced vertices"};
  }
  throw Error("GLB command operation is not allowlisted");
}
