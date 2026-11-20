// Lossless Model Forge document handoff. URL fragments are not sent to the host.
// Shared links are NOT private or encrypted; use for non-sensitive sample models.
const LIMIT = 8192;
const encoder = new TextEncoder();
const decoder = new TextDecoder("utf-8", {fatal: true});
function checksum(bytes) {
  let h = 2166136261;
  for (const b of bytes) h = Math.imul(h ^ b, 16777619);
  return (h >>> 0).toString(16).padStart(8, "0");
}
export function encodeSession(text) {
  if (typeof text !== "string" || !/^MFORGE_DOC [1-5]\n/.test(text))
    throw new Error("Only native editable .mforge projects can be shared");
  const bytes = encoder.encode(text);
  if (bytes.length > LIMIT)
    throw new Error("Model too large for a link. Save and attach the .mforge file instead.");
  let binary = "";
  for (let i = 0; i < bytes.length; i += 2048)
    binary += String.fromCharCode(...bytes.subarray(i, i + 2048));
  const encoded = btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return "#mf1=" + encoded + "." + checksum(bytes);
}
export function decodeSession(hash) {
  if (!hash || hash === "#" || !hash.startsWith("#mf1=")) return null;
  const token = hash.slice(5);
  if (token.length > 12000) throw new Error("Shared link too long");
  const match = /^([A-Za-z0-9_-]+)\.([0-9a-f]{8})$/.exec(token);
  if (!match) throw new Error("Invalid collaboration link format");
  const base64 = match[1].replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64 + "=".repeat((4 - base64.length % 4) % 4));
  if (raw.length > LIMIT) throw new Error("Shared link exceeds size limit");
  const bytes = Uint8Array.from(raw, c => c.charCodeAt(0));
  if (checksum(bytes) !== match[2]) throw new Error("Collaboration link checksum mismatch");
  const text = decoder.decode(bytes);
  if (!/^MFORGE_DOC [1-5]\n/.test(text)) throw new Error("Not a native Model Forge project");
  return text;
}
