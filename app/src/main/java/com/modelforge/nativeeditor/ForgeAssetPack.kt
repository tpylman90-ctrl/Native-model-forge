package com.modelforge.nativeeditor

import android.content.Context
import com.google.gson.JsonParser
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Compressed, offline-first GLB catalog.
 *
 * The ZIP container is stored intact in private app storage; ZipFile opens
 * only manifest.json while browsing and inflates only the chosen GLB upon use.
 * This keeps installed object count independent from Java heap occupancy.
 */
class ForgeAssetPack private constructor(
    val file: File,
    val packId: String,
    val title: String,
    val entries: List<Entry>
) {
    data class Entry(
        val id:String,
        val title:String,
        val category:String,
        val file:String,
        val bytes:Int,
        val sha256:String,
        val license:String,
        val source:String
    )
    companion object {
        private const val MAX_PACK_BYTES=80L*1024*1024
        private const val MAX_OBJECT_BYTES=8*1024*1024
        private const val MAX_MANIFEST_BYTES=1024*1024
        private const val PACK_ASSET="packs/forge-basics.mfpack"

        private fun readCapped(input:InputStream,max:Int):ByteArray {
            val result=java.io.ByteArrayOutputStream()
            val scratch=ByteArray(8192)
            while(true) {
                val count=input.read(scratch)
                if(count<0)break
                require(result.size()+count<=max) {"Asset exceeds safe uncompressed size"}
                result.write(scratch,0,count)
            }
            return result.toByteArray()
        }
        private fun digest(bytes:ByteArray):String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString(""){"%02x".format(it)}

        fun open(file:File):ForgeAssetPack {
            require(file.isFile && file.length() in 1..MAX_PACK_BYTES) {
                "Invalid or oversized asset pack"
            }
            ZipFile(file).use { zip ->
                require(zip.size() in 2..600) {"Asset pack has too many entries"}
                val manifest=zip.getEntry("manifest.json")
                    ?:throw IllegalArgumentException("Asset pack manifest.json missing")
                require(!manifest.isDirectory && manifest.size in 1..MAX_MANIFEST_BYTES.toLong()) {
                    "Invalid asset pack manifest"
                }
                val data=zip.getInputStream(manifest).use {readCapped(it,MAX_MANIFEST_BYTES)}
                val root=JsonParser.parseString(String(data,Charsets.UTF_8)).asJsonObject
                require(root.get("format")?.asString=="model-forge-pack" &&
                    root.get("version")?.asInt==1) {"Unsupported asset pack format"}
                val packId=root.get("pack_id").asString
                val title=root.get("title").asString
                require(packId.matches(Regex("[A-Za-z0-9_-]{2,80}")) &&
                    title.length in 1..90) {"Invalid pack identity"}
                val array=root.getAsJsonArray("assets")
                    ?:throw IllegalArgumentException("Pack contains no catalog")
                require(array.size() in 1..500) {"Pack must contain 1–500 assets"}
                val seen=HashSet<String>()
                val found=array.map { raw ->
                    val a=raw.asJsonObject
                    val id=a.get("id").asString
                    val name=a.get("title").asString
                    val category=a.get("category").asString
                    val fileName=a.get("file").asString
                    val size=a.get("bytes").asInt
                    val sha=a.get("sha256").asString.lowercase()
                    val license=a.get("license")?.asString ?: "unknown"
                    val source=a.get("source")?.asString ?: "unspecified"
                    require(id.matches(Regex("[a-zA-Z0-9_-]{1,75}")) &&
                            seen.add(id) && name.length in 1..100 &&
                            category.length in 1..80 &&
                            fileName.matches(Regex("glbs/[A-Za-z0-9_-]{1,100}\.glb")) &&
                            size in 28..MAX_OBJECT_BYTES &&
                            sha.matches(Regex("[0-9a-f]{64}")) &&
                            license.length in 2..90 && source.length in 1..200) {
                        "Invalid asset manifest entry or duplicate ID"
                    }
                    val entry=zip.getEntry(fileName)
                        ?:throw IllegalArgumentException("Missing asset: $fileName")
                    require(!entry.isDirectory && entry.size==size.toLong()) {
                        "Invalid asset size for $name"
                    }
                    Entry(id,name,category,fileName,size,sha,license,source)
                }
                return ForgeAssetPack(file,packId,title,found)
            }
        }

        fun bundled(context:Context):ForgeAssetPack {
            val directory=File(context.noBackupFilesDir,"forge-asset-packs")
            require(directory.isDirectory || directory.mkdirs()) {"Cannot create asset pack cache"}
            val destination=File(directory,"forge-basics-v1.mfpack")
            if(!destination.isFile || destination.length()==0L) {
                val temp=File(directory,"forge-basics-v1.tmp")
                try {
                    context.assets.open(PACK_ASSET).use { source ->
                        temp.outputStream().use { target ->
                            val copied=source.copyTo(target,8192)
                            require(copied in 1..MAX_PACK_BYTES) {"Built-in library is oversized"}
                        }
                    }
                    require(temp.renameTo(destination)) {"Could not cache compressed basic pack"}
                } finally {temp.delete()}
            }
            return try {open(destination)}
            catch(error:Exception) {
                // A corrupted app-private pack must not remain silently trusted.
                destination.delete()
                throw error
            }
        }

        fun installExternal(context:Context,input:InputStream):ForgeAssetPack {
            val directory=File(context.filesDir,"forge-asset-packs")
            require(directory.isDirectory || directory.mkdirs()) {"Cannot store asset pack"}
            val temp=File(directory,"import-pending.mfpack")
            try {
                temp.outputStream().use { output ->
                    val buffer=ByteArray(16384)
                    var total=0L
                    while(true) {
                        val count=input.read(buffer)
                        if(count<0)break
                        total+=count
                        require(total<=MAX_PACK_BYTES) {"Compressed pack exceeds 80 MB"}
                        output.write(buffer,0,count)
                    }
                }
                val validated=open(temp)
                val finalFile=File(directory,validated.packId+".mfpack")
                if(finalFile.exists())require(finalFile.delete()){"Cannot replace old pack"}
                require(temp.renameTo(finalFile)) {"Cannot save new pack"}
                return open(finalFile)
            } finally {temp.delete()}
        }
    }

    /** Inflate exactly one ZIP member into memory; never unpack whole packs. */
    fun selectedGlb(assetId:String):ByteArray {
        val target=entries.firstOrNull{it.id==assetId}
            ?:throw IllegalArgumentException("Unknown library object")
        ZipFile(file).use { archive ->
            val member=archive.getEntry(target.file)
                ?:throw IllegalArgumentException("Object is missing from compressed pack")
            require(member.size==target.bytes.toLong()) {"Asset size changed"}
            val bytes=archive.getInputStream(member).use { input ->
                readCapped(input,target.bytes)
            }
            require(bytes.size==target.bytes && digest(bytes)==target.sha256) {
                "Object checksum does not match its catalog"
            }
            require(GlbBuilder.isValidHeader(bytes)) {"Object GLB is invalid"}
            return bytes
        }
    }
}
