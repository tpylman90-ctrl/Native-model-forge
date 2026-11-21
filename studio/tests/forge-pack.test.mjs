import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {openForgePack} from "../web/forge_pack.mjs";
import {GlbWorkshop} from "../web/glb_workshop.mjs";

const packPath=new URL("../web/packs/forge-basics.mfpack",import.meta.url);
test("Android-compatible compressed CC0 Forge Basics library lazily opens 18 GLBs",async()=>{
  const packed=new Uint8Array(fs.readFileSync(packPath));
  const library=await openForgePack(packed);
  assert.equal(library.manifest.format,"model-forge-pack");
  assert.equal(library.manifest.assets.length,18);
  assert.equal(library.manifest.license,"CC0-1.0");
  const names=library.manifest.assets.map(x=>x.id);
  for(const required of ["cube","fairing","radiator_grille","intake_frame","thruster_housing","aero_fin"])
    assert.ok(names.includes(required),"missing CC0 part "+required);
  const sample=library.manifest.assets.find(x=>x.id==="fairing");
  const bytes=await library.getAsset(sample);
  assert.equal(bytes.length,sample.bytes);
  const workshop=new GlbWorkshop(bytes);
  assert.equal(workshop.parts().length,1);
  assert.match(workshop.parts()[0].name,/fairing/i);
});
test("pack reader rejects corrupt ZIP and catalog data safely",async()=>{
  const packed=new Uint8Array(fs.readFileSync(packPath));
  assert.rejects(openForgePack(new Uint8Array([1,2,3,4])));
  const modified=packed.slice();
  modified.set([0,0,0,0],modified.length-22);
  await assert.rejects(()=>openForgePack(modified));
});
test("CC0 library part inserts into an existing GLB with original materials untouched",async()=>{
  const library=await openForgePack(new Uint8Array(fs.readFileSync(packPath)));
  const cube=await library.getAsset(library.manifest.assets.find(x=>x.id==="cube"));
  const grille=await library.getAsset(library.manifest.assets.find(x=>x.id==="radiator_grille"));
  const model=new GlbWorkshop(cube);
  const original=model.getBytes();
  const preview=model.previewLibraryPart(grille,"New grille");
  assert.deepEqual(model.getBytes(),original,"preview must not commit geometry");
  const added=model.addLibraryPart(grille,"New grille");
  assert.equal(added.node,1);
  assert.deepEqual(added.bytes,preview.bytes);
  assert.equal(model.parts().length,2);
  assert.equal(model.parts()[1].name,"New grille");
  assert.deepEqual(model.undo(),original);
  assert.deepEqual(model.redo(),added.bytes);
});
