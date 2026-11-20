// Exercise the compiled WebAssembly engine, including deliberate invalid edits.
// Regression: with exception catching disabled, invalid input aborted the module.
import fs from "node:fs";
import assert from "node:assert/strict";
import createForgeCore from "../web/forge_core.js";
const binary=fs.readFileSync(new URL("../web/forge_core.wasm",import.meta.url));
const wasm=await createForgeCore({
  instantiateWasm:(imports,complete)=>{
    WebAssembly.instantiate(binary,imports)
      .then(({instance})=>complete(instance))
      .catch(err=>{console.error(err);process.exitCode=1;});
    return {};
  }
});
const call=(name,types=[],args=[],result="number")=>wasm.ccall(name,result,types,args);
const n=x=>Array(x).fill("number");
assert.equal(call("mf_new_box",n(3),[4.5,1.8,8.4]),1);
assert.equal(call("mf_vertex_count"),36);
const first=call("mf_save_project",[],[],"string");
assert.match(first,/^MFORGE_DOC 5/);
assert.equal(call("mf_set_region",n(12),[4,2,.5,.6,0,0,.25,.08,-.06,.12,0,1]),1);
assert.equal(call("mf_vertex_count"),426);
assert.equal(call("mf_mirror",n(3),[4,0,2]),1);
assert.equal(call("mf_vertex_count"),816);
assert.equal(call("mf_undo",n(1),[3]),1);
assert.equal(call("mf_vertex_count"),426);
assert.equal(call("mf_redo",n(1),[4]),1);
const saved=call("mf_save_project",[],[],"string");
assert.equal(call("mf_load_project",["string"],[saved]),1);
assert.equal(call("mf_save_project",[],[],"string"),saved);
assert.equal(call("mf_set_region",n(12),[4,2,.99,.5,0,0,.25,0,0,0,0,5]),0);
assert.match(call("mf_last_error",[],[],"string"),/invalid/);
assert.equal(call("mf_save_project",[],[],"string"),saved);
assert.equal(call("mf_vertex_count"),816);
assert.equal(call("mf_reshape",n(7),[4.8,2,8.4,1,1,0,4]),0);
assert.match(call("mf_last_error",[],[],"string"),/stale/);
assert.equal(call("mf_load_project",["string"],[first]),1);
assert.equal(call("mf_vertex_count"),36);
const ptr=call("mf_vertex_data");
assert.ok(Number.isFinite(wasm.HEAPF32[ptr/4]));
const sharedHull=fs.readFileSync(new URL("../web/sessions/hovercraft-hull.mforge",import.meta.url),"utf8");
assert.equal(call("mf_load_project",["string"],[sharedHull]),1,"shared hull must load in native C++ document");
const loaded=JSON.parse(call("mf_state_json",[],[],"string"));
assert.equal(loaded.name,"Co-op Hover Hull 01");
assert.equal(loaded.faces[0].kind,2);
assert.equal(loaded.faces[4].kind,2);
assert.ok(call("mf_vertex_count")>36,"shared hull must contain real surface geometry");
const canonical=call("mf_save_project",[],[],"string");
// Hand-authored decimal parameters normalize to 17-digit C++ output.
assert.equal(call("mf_load_project",["string"],[canonical]),1);
assert.equal(call("mf_save_project",[],[],"string"),canonical);
console.log("SHARED_HULL_NATIVE: PASS project parses and tessellates in WebAssembly core");

console.log("WASM_RUNTIME_SMOKE: PASS native geometry, curves, mirror, undo, atomic invalid edit, stale revision, reload");
