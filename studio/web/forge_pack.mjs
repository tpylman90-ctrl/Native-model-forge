/**
 * Browser reader for the identical ZIP-backed CC0 .mfpack used on Android.
 * No JSZip, no third-party runtime, and no expansion of the full archive:
 * only manifest.json and a chosen GLB are inflated using DecompressionStream.
 */
const MAX_PACK=80*1024*1024, MAX_MANIFEST=1024*1024,MAX_OBJECT=8*1024*1024;
const decoder=new TextDecoder("utf-8",{fatal:true});
const zipSignature={end:0x06054b50,central:0x02014b50,local:0x04034b50};
const requireBound=(condition,message)=>{if(!condition)throw Error(message);};
function text(bytes){return decoder.decode(bytes);}
async function inflate(bytes,method,maxLength){
  if(method===0){
    requireBound(bytes.length<=maxLength,"Archive entry exceeds declared size limit");
    return bytes.slice();
  }
  if(method!==8)throw Error("Unsupported ZIP compression method");
  if(typeof DecompressionStream!=="function")throw Error("This browser cannot unpack raw-deflate asset packs");
  const ds=new DecompressionStream("deflate-raw");
  const reader=new Blob([bytes]).stream().pipeThrough(ds).getReader();
  const chunks=[];let total=0;
  try{
    while(true){
      const {value,done}=await reader.read();
      if(done)break;
      total+=value.byteLength;
      if(total>maxLength){await reader.cancel();throw Error("Decompressed asset exceeds safe size limit");}
      chunks.push(value);
    }
  }finally{reader.releaseLock();}
  const result=new Uint8Array(total);
  let offset=0;for(const chunk of chunks){result.set(chunk,offset);offset+=chunk.length;}
  return result;
}
class Reader {
  constructor(input){
    this.bytes=input instanceof Uint8Array?input:new Uint8Array(input);
    requireBound(this.bytes.length>22&&this.bytes.length<=MAX_PACK,"Invalid or oversized Model Forge asset pack");
    this.dv=new DataView(this.bytes.buffer,this.bytes.byteOffset,this.bytes.byteLength);
    this.entries=new Map();
    this.parseDirectory();
  }
  parseDirectory(){
    const size=this.bytes.length,v=this.dv;
    let end=-1;
    for(let i=size-22;i>=Math.max(0,size-65557);i--){
      if(v.getUint32(i,true)===zipSignature.end && i+22+v.getUint16(i+20,true)===size){
        end=i;break;
      }
    }
    requireBound(end>=0,"Asset pack ZIP directory footer missing");
    const count=v.getUint16(end+10,true),centralSize=v.getUint32(end+12,true),
      centralOffset=v.getUint32(end+16,true);
    requireBound(count>=2&&count<=600&&centralOffset+centralSize<=end,
      "Asset pack directory is malformed or contains too many entries");
    let at=centralOffset;
    for(let i=0;i<count;i++){
      requireBound(at+46<=end&&v.getUint32(at,true)===zipSignature.central,
        "Asset pack central directory corrupted");
      const flags=v.getUint16(at+8,true),method=v.getUint16(at+10,true);
      const compressed=v.getUint32(at+20,true),uncompressed=v.getUint32(at+24,true);
      const nameLen=v.getUint16(at+28,true),extraLen=v.getUint16(at+30,true),
        commentLen=v.getUint16(at+32,true),local=v.getUint32(at+42,true);
      const next=at+46+nameLen+extraLen+commentLen;
      requireBound(!(flags&1) && !((flags&8) && local>=size) &&
        next<=end&&nameLen>0&&compressed<=size&&uncompressed<=MAX_OBJECT,
        "Encrypted, oversized or malformed library ZIP entry");
      const name=text(this.bytes.subarray(at+46,at+46+nameLen));
      requireBound(name==="manifest.json"||/^glbs\/[A-Za-z0-9_-]{1,100}\.glb$/.test(name),
        "Unapproved path inside Model Forge asset pack");
      requireBound(!this.entries.has(name),"Duplicate filename inside part pack");
      requireBound(local+30<=centralOffset&&v.getUint32(local,true)===zipSignature.local,
        "Library entry offset invalid");
      const localName=v.getUint16(local+26,true),localExtra=v.getUint16(local+28,true);
      const dataStart=local+30+localName+localExtra;
      requireBound(dataStart+compressed<=centralOffset,"Part extends outside ZIP data region");
      this.entries.set(name,{method,compressed,uncompressed,start:dataStart});
      at=next;
    }
    requireBound(at===centralOffset+centralSize && this.entries.has("manifest.json"),
      "Missing or inconsistent part catalog manifest");
  }
  async read(name,cap=MAX_OBJECT){
    const entry=this.entries.get(name);
    if(!entry)throw Error("Part is not in the compressed catalog");
    requireBound(entry.uncompressed>=0&&entry.uncompressed<=cap,"Invalid asset size");
    const encoded=this.bytes.subarray(entry.start,entry.start+entry.compressed);
    const decoded=await inflate(encoded,entry.method,cap);
    requireBound(decoded.length===entry.uncompressed,"Part ZIP size mismatch");
    return decoded;
  }
}
export async function openForgePack(input){
  const zip=new Reader(input);
  const manifestBytes=await zip.read("manifest.json",MAX_MANIFEST);
  const manifest=JSON.parse(text(manifestBytes));
  requireBound(manifest.format==="model-forge-pack"&&manifest.version===1,
    "Unsupported Model Forge asset library");
  requireBound(Array.isArray(manifest.assets)&&manifest.assets.length>0&&manifest.assets.length<=500,
    "Invalid library manifest");
  const seen=new Set();
  for(const asset of manifest.assets){
    requireBound(typeof asset.id==="string"&&/^[A-Za-z0-9_-]{1,75}$/.test(asset.id)&&
      !seen.has(asset.id)&&typeof asset.title==="string"&&asset.title.length>0&&
      typeof asset.file==="string"&&zip.entries.has(asset.file)&&
      Number.isInteger(asset.bytes)&&asset.bytes>=28&&asset.bytes<=MAX_OBJECT&&
      typeof asset.sha256==="string"&&/^[0-9a-f]{64}$/i.test(asset.sha256),
      "Invalid or duplicate entry in library manifest");
    const info=zip.entries.get(asset.file);
    requireBound(info.uncompressed===asset.bytes,"Catalog asset byte length mismatch");
    seen.add(asset.id);
  }
  return {
    manifest,
    async getAsset(asset){
      const record=manifest.assets.find(a=>a.id===asset.id);
      if(!record)throw Error("Unknown asset identifier");
      const bytes=await zip.read(record.file,MAX_OBJECT);
      if(!globalThis.crypto?.subtle)throw Error("SHA-256 verification requires secure HTTPS");
      const digest=new Uint8Array(await crypto.subtle.digest("SHA-256",bytes));
      const digestHex=Array.from(digest,b=>b.toString(16).padStart(2,"0")).join("");
      if(digestHex!==record.sha256.toLowerCase())throw Error("Part asset failed SHA-256 integrity verification");
      return bytes;
    }
  };
}
