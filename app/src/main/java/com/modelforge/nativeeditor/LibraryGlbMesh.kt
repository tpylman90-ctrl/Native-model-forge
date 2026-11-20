package com.modelforge.nativeeditor

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Convert one supported GLB mesh into the native six-float packed triangles.
 * This importer intentionally rejects unsupported topology/material structures,
 * rather than quietly corrupting a third-party Blender file.
 */
object LibraryGlbMesh {
    data class Result(val name:String,val vertices:FloatArray,val color:FloatArray)

    fun read(bytes:ByteArray):Result {
        require(GlbBuilder.isValidHeader(bytes)) {"Invalid library GLB header"}
        val input=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        input.position(12)
        val jsonLength=input.int
        require(jsonLength in 4..(1024*1024) && jsonLength%4==0) {
            "Asset JSON exceeds library mesh decoder limit"
        }
        require(input.int==0x4e4f534a && jsonLength<=input.remaining()) {
            "Asset JSON chunk is malformed"
        }
        val jsonBytes=ByteArray(jsonLength);input.get(jsonBytes)
        val root=JsonParser.parseString(String(jsonBytes,Charsets.UTF_8).trimEnd(' ','\u0000'))
            .asJsonObject
        require(input.remaining()>=8) {"Asset missing binary geometry"}
        val binLength=input.int
        require(input.int==0x004e4942 && binLength>=0 && binLength==input.remaining()) {
            "Asset BIN chunk mismatch"
        }
        val start=input.position()
        val bin=input.slice().order(ByteOrder.LITTLE_ENDIAN)
        val buffers=root.getAsJsonArray("buffers")
        require(buffers!=null && buffers.size()==1 &&
            buffers[0].asJsonObject.get("byteLength").asInt<=binLength) {
            "Only single embedded-buffer GLBs can be inserted"
        }
        val meshes=root.getAsJsonArray("meshes")
        require(meshes!=null && meshes.size()==1) {
            "Multi-mesh external assets need conversion to one mesh in Blender first"
        }
        val mesh=meshes[0].asJsonObject
        val primitives=mesh.getAsJsonArray("primitives")
        require(primitives!=null && primitives.size()==1 &&
            (primitives[0].asJsonObject.get("mode")?.asInt?:4)==4) {
            "Asset must contain one triangle-mesh primitive"
        }
        val nodes=root.getAsJsonArray("nodes")
        // The original essentials pack is exported with identity transform.
        // Reject non-identity nodes instead of importing at an incorrect pose.
        require(nodes!=null && nodes.size()==1 && !nodes[0].asJsonObject.has("skin")) {
            "Asset must have one unskinned mesh node"
        }
        val node=nodes[0].asJsonObject
        require(!node.has("matrix") && !node.has("translation") &&
            !node.has("rotation") && !node.has("scale")) {
            "Apply transforms in Blender before packing this GLB"
        }
        val primitive=primitives[0].asJsonObject
        val attrs=primitive.getAsJsonObject("attributes")
            ?:throw IllegalArgumentException("Asset has no mesh attributes")
        val pos=attrs.get("POSITION")?.asInt
            ?:throw IllegalArgumentException("Asset lacks POSITION")
        val normal=attrs.get("NORMAL")?.asInt
        val accessors=root.getAsJsonArray("accessors")
            ?:throw IllegalArgumentException("Missing mesh accessors")
        val views=root.getAsJsonArray("bufferViews")
            ?:throw IllegalArgumentException("Missing mesh buffer views")
        fun accessor(id:Int,components:Int):Triple<Int,Int,Int> {
            require(id in 0 until accessors.size()) {"Invalid vertex accessor"}
            val a=accessors[id].asJsonObject
            require(a.get("componentType").asInt==5126 &&
                a.get("type").asString==(if(components==3)"VEC3" else "SCALAR") &&
                !a.has("sparse")) {"Only float32 unsparsed position/normal attributes supported"}
            val viewId=a.get("bufferView")?.asInt
                ?:throw IllegalArgumentException("Missing attribute buffer view")
            require(viewId in 0 until views.size()) {"Invalid buffer view"}
            val view=views[viewId].asJsonObject
            require((view.get("buffer")?.asInt?:0)==0) {"External vertex buffer unsupported"}
            val count=a.get("count").asInt
            val stride=view.get("byteStride")?.asInt?:components*4
            require(count in 1..100_000 && stride in components*4..256 && stride%4==0) {
                "Invalid vertex count/stride"
            }
            val viewOffset=view.get("byteOffset")?.asInt?:0
            val relative=a.get("byteOffset")?.asInt?:0
            val offset=viewOffset.toLong()+relative.toLong()
            val viewLength=view.get("byteLength").asLong
            require(offset>=viewOffset && offset+(count-1L)*stride+components*4 <=
                    viewOffset.toLong()+viewLength &&
                    offset+(count-1L)*stride+components*4 <= binLength &&
                    offset<=Int.MAX_VALUE) {"Vertex accessor extends outside GLB"}
            return Triple(offset.toInt(),stride,count)
        }
        val p=accessor(pos,3)
        val n=normal?.let {accessor(it,3)}
        require(n==null || n.third==p.third) {"Normal and position counts differ"}

        fun vec(a:Triple<Int,Int,Int>,index:Int):FloatArray {
            require(index in 0 until a.third) {"Mesh index out of range"}
            val base=a.first+index*a.second
            return floatArrayOf(bin.getFloat(base),bin.getFloat(base+4),bin.getFloat(base+8))
                .also { require(it.all(Float::isFinite)) {"Asset contains NaN coordinates"} }
        }
        val indices=primitive.get("indices")?.asInt?.let { id ->
            require(id in 0 until accessors.size()) {"Invalid triangle indices"}
            val a=accessors[id].asJsonObject
            require(a.get("type").asString=="SCALAR" && !a.has("sparse")) {
                "Unsupported triangle index accessor"
            }
            val type=a.get("componentType").asInt
            val width=when(type){5121->1;5123->2;5125->4;else->
                throw IllegalArgumentException("Unsupported triangle index format")}
            val viewId=a.get("bufferView").asInt
            require(viewId in 0 until views.size()) {"Invalid indices view"}
            val view=views[viewId].asJsonObject
            val count=a.get("count").asInt
            require(count in 3..100_000 && count%3==0 &&
                (view.get("buffer")?.asInt?:0)==0) {"Invalid index buffer"}
            val stride=view.get("byteStride")?.asInt?:width
            require(stride>=width && stride<=256) {"Invalid index stride"}
            val offset=(view.get("byteOffset")?.asInt?:0).toLong()+
                (a.get("byteOffset")?.asInt?:0).toLong()
            require(offset>=0 && offset+(count-1L)*stride+width<=binLength &&
                offset+(count-1L)*stride+width <=
                (view.get("byteOffset")?.asInt?:0).toLong()+view.get("byteLength").asLong) {
                "Index buffer outside GLB"
            }
            IntArray(count) {i ->
                val at=(offset+i*stride).toInt()
                val v=when(width){
                    1->bin.get(at).toInt()and 255
                    2->bin.getShort(at).toInt()and 65535
                    else->{
                        val value=bin.getInt(at)
                        require(value>=0) {"Invalid large mesh index"}
                        value
                    }
                }
                require(v<p.third) {"Indexed asset references missing vertex"}
                v
            }
        } ?: IntArray(p.third){it}
        require(indices.size>=3 && indices.size%3==0 &&
            indices.size<=100_000) {"Triangle list is malformed or too large"}
        val verts=FloatArray(indices.size*6)
        for(tri in indices.indices step 3) {
            val a=vec(p,indices[tri]);val b=vec(p,indices[tri+1])
            val c=vec(p,indices[tri+2])
            val ux=b[0]-a[0];val uy=b[1]-a[1];val uz=b[2]-a[2]
            val vx=c[0]-a[0];val vy=c[1]-a[1];val vz=c[2]-a[2]
            val nx=uy*vz-uz*vy;val ny=uz*vx-ux*vz;val nz=ux*vy-uy*vx
            val mag=sqrt(nx*nx+ny*ny+nz*nz)
            require(mag.isFinite()&&mag>1e-10f) {"Degenerate face in library GLB"}
            for(i in 0..2) {
                val index=indices[tri+i]
                val point=when(i){0->a;1->b;else->c}
                val normalVec=n?.let {vec(it,index)}?:floatArrayOf(nx/mag,ny/mag,nz/mag)
                val base=(tri+i)*6
                point.copyInto(verts,base)
                normalVec.copyInto(verts,base+3)
            }
        }
        val materials=root.getAsJsonArray("materials")
        val color=try {
            val idx=primitive.get("material")?.asInt?:-1
            if(materials!=null && idx in 0 until materials.size()) {
                val rgba=materials[idx].asJsonObject
                    .getAsJsonObject("pbrMetallicRoughness")
                    ?.getAsJsonArray("baseColorFactor")
                if(rgba!=null && rgba.size()==4) FloatArray(4){rgba[it].asFloat}
                else floatArrayOf(0.35f,0.57f,0.66f,1f)
            } else floatArrayOf(0.35f,0.57f,0.66f,1f)
        } catch(_:Exception) {floatArrayOf(0.35f,0.57f,0.66f,1f)}
        require(color.all {it.isFinite()&&it in 0f..1f}){"Invalid GLB base color"}
        return Result(mesh.get("name")?.asString?:"Imported object",verts,color)
    }
}
