import test from "node:test";
import assert from "node:assert/strict";
import {encodeSession, decodeSession} from "../web/share_session.mjs";
test("round-trip document via URL fragment unchanged", () => {
  const project='MFORGE_DOC 5\n3 2 1\n1 1 "Co-op Hull" "generated:profiled_box" 4.5 1.8 8.4 1 1 0\n';
  const hash=encodeSession(project);
  assert.match(hash,/^#mf1=[A-Za-z0-9_-]+\.[0-9a-f]{8}$/);
  assert.equal(decodeSession(hash),project);
  assert.equal(decodeSession(""),null);
  assert.equal(decodeSession("#unrelated"),null);
  const corrupt=hash.slice(0,-1)+(hash.endsWith("a")?"b":"a");
  assert.throws(()=>decodeSession(corrupt),/checksum/);
});
test("Unicode name, invalid and overly large data",()=>{
  const project='MFORGE_DOC 5\n1 2 1\n1 1 "Élan 火星" "generated:profiled_box" 4.5 1.8 8.4 1 1 0\n';
  assert.equal(decodeSession(encodeSession(project)),project);
  assert.throws(()=>encodeSession("not a project"));
  assert.throws(()=>encodeSession("MFORGE_DOC 5\n"+"a".repeat(10000)));
  assert.throws(()=>decodeSession("#mf1=abc"));
});
