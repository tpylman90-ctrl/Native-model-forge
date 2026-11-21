import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {openForgePack} from "../web/forge_pack.mjs";
import {GlbWorkshop} from "../web/glb_workshop.mjs";
import {templateGlbCommand,reviewGlbCommand} from "../web/glb_command.mjs";

async function create(){
  const archive=await openForgePack(new Uint8Array(fs.readFileSync(
    new URL("../web/packs/forge-basics.mfpack",import.meta.url))));
  const cube=archive.manifest.assets.find(a=>a.id==="cube");
  return new GlbWorkshop(await archive.getAsset(cube));
}
test("GLB command review is atomic and requires explicit apply",async()=>{
  const w=await create(),source=w.getBytes();
  const template=templateGlbCommand(w,0,"transform");
  assert.equal(template.protocol,"model-forge/glb-command/v1");
  assert.equal(template.base_revision,1);
  const proposal={...template,parameters:{move_m:[1,0,0],turn_degrees:[0,30,0],scale:[1,1,1]}};
  const inspected=reviewGlbCommand(w,JSON.stringify(proposal));
  assert.equal(inspected.kind,"transform");
  assert.equal(inspected.node,0);
  assert.ok(inspected.bytes.length>=source.length);
  assert.deepEqual(w.getBytes(),source,"review must not mutate committed geometry");
  const applied=w.applyTransform(inspected.node,inspected.args);
  assert.deepEqual(applied,inspected.bytes);
  assert.throws(()=>reviewGlbCommand(w,JSON.stringify(proposal)),/Stale/);
});
test("GLB brush command validates finite local geometry, revision and target node",async()=>{
  const w=await create(),source=w.getBytes();
  const template=templateGlbCommand(w,0,"brush",0);
  assert.equal(template.parameters.primitive_index,0);
  template.parameters.radius_local=1.25;
  template.parameters.delta_local=[0,.2,0];
  const result=reviewGlbCommand(w,JSON.stringify(template));
  assert.equal(result.kind,"brush");
  assert.ok(result.affected>0);
  assert.deepEqual(w.getBytes(),source);
  for(const invalid of [
    {...template,node_id:999},
    {...template,base_revision:0},
    {...template,protocol:"some-other-protocol"},
    {...template,requires_approval:false},
    {...template,operation:"delete_all_meshes"},
    {...template,parameters:{...template.parameters,radius_local:"oops"}}
  ]){
    assert.throws(()=>reviewGlbCommand(w,JSON.stringify(invalid)));
  }
  assert.deepEqual(w.getBytes(),source);
});
