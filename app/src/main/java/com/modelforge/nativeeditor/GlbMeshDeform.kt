package com.modelforge.nativeeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * First real imported-mesh edit. Makes a bounded, spatially weighted change to
 * POSITION coordinates, rebuilds NORMAL, and leaves original binary untouched.
 *
 * Replacements are appended as fresh bufferViews/accessors. This is essential:
 * GlbWorkshop undo snapshots record JSON + used binary length, not old byte arrays.
 * It also means shared accessors and unrelated primitives are not mutated.
 *
 * Only unskinned, unanimated, ordinary glTF triangles are accepted. Compressed,
 * sparse, morphed and tangent-space geometries are refused, never silently
 * corrupted. Coordinates and brush deltas are in the selected mesh's LOCAL space.
 */
internal object GlbMeshDeform {
    data class Brush(
        val primitive: Int,
        val centerX: Float, val centerY: Float, val centerZ: Float,
        val radius: Float,
        val moveX: Float, val moveY: Float, val moveZ: Float
    )
    data class Surface(
        val primitive: Int, val vertexCount: Int,
        val min: FloatArray, val max: FloatArray
    )
    data class Result(val root: JsonObject, val binary: ByteArray, val length: Int)

    private data class View(val offset: Int, val stride: Int, val count: Int)
    private data class Input(val positions: FloatArray, val indices: IntArray, val primitive: JsonObject)

    private fun arrays(root: JsonObject, name: String): JsonArray =
        root.getAsJsonArray(name) ?: throw IllegalArgumentException("GLB lacks $name")

    private fun nodeMesh(root: JsonObject, nodeId: Int): Int {
        val nodes=arrays(root,"nodes")
        require(nodeId in 0 until nodes.size()) {"Invalid selected node"}
        val node=nodes[nodeId].asJsonObject
        require(node.has("mesh") && !node.has("skin")) {"Only unskinned mesh nodes can be sculpted"}
        val meshId=node.get("mesh").asInt
        require(meshId in 0 until arrays(root,"meshes").size()) {"Invalid mesh"}
        return meshId
    }
    private fun getPrimitive(root: JsonObject, meshId: Int, primitiveId: Int): JsonObject {
        val mesh=arrays(root,"meshes")[meshId].asJsonObject
        require(!mesh.has("weights")) {"Morph-target meshes are not yet sculptable"}
        val prims=mesh.getAsJsonArray("primitives") ?: throw IllegalArgumentException("Mesh has no primitives")
        require(primitiveId in 0 until prims.size()) {"Select a valid mesh surface"}
        return prims[primitiveId].asJsonObject.also { p ->
            require((p.get("mode")?.asInt ?: 4)==4) {"Only triangle-list mesh surfaces are editable"}
            require(!p.has("targets")) {"Morph-target geometry is not editable yet"}
            val ext=p.getAsJsonObject("extensions")
            require(ext?.has("KHR_draco_mesh_compression")!=true) {"Decode Draco before editing this mesh"}
            require(p.getAsJsonObject("attributes")?.has("TANGENT")!=true) {
                "Tangent-space meshes need tangent recalculation; edit not applied"
            }
        }
    }

    private fun accessor(root:JsonObject, id:Int, components:Int, countLimit:Int,
                         usedBytes:Int): View {
        val accessors=arrays(root,"accessors")
        require(id in 0 until accessors.size()) {"Invalid GLB accessor"}
        val a=accessors[id].asJsonObject
        require((a.get("componentType")?.asInt?:0)==5126 &&
                a.get("type")?.asString==(if(components==3)"VEC3" else "SCALAR") &&
                !a.has("sparse") && a.get("normalized")?.asBoolean!=true) {
            "This surface needs ordinary, non-sparse FLOAT32 positions"
        }
        val count=a.get("count")?.asInt?:0
        require(count in 1..countLimit) {"Mesh surface exceeds safe vertex limit"}
        val views=arrays(root,"bufferViews")
        val viewId=a.get("bufferView")?.asInt?:-1
        require(viewId in 0 until views.size()) {"Missing geometry buffer view"}
        val v=views[viewId].asJsonObject
        require((v.get("buffer")?.asInt?:0)==0 && !v.has("extensions")) {
            "Compressed or external buffer views must be decoded first"
        }
        val start=v.get("byteOffset")?.asLong?:0L
        val size=v.get("byteLength")?.asLong?:-1L
        val relative=a.get("byteOffset")?.asLong?:0L
        val stride=v.get("byteStride")?.asInt?:components*4
        val offset=start+relative
        val last=offset+(count-1L)*stride+components*4
        require(start>=0 && size>=0 && relative>=0 && offset%4==0L &&
                stride in (components*4)..256 && stride%4==0 &&
                last<=start+size && last<=usedBytes.toLong() &&
                offset<=Int.MAX_VALUE) {
            "Position accessor extends outside the GLB binary"
        }
        return View(offset.toInt(),stride,count)
    }

    private fun readPositions(root: JsonObject, primitive: JsonObject, binary: ByteArray,
                              length: Int): FloatArray {
        val attributes=primitive.getAsJsonObject("attributes")
            ?: throw IllegalArgumentException("Missing mesh attributes")
        val position=attributes.get("POSITION")?.asInt
            ?: throw IllegalArgumentException("Mesh has no position data")
        val view=accessor(root,position,3,150_000,length)
        val bb=ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(view.count*3) { i ->
            bb.getFloat(view.offset+(i/3)*view.stride+(i%3)*4).also {
                require(it.isFinite() && kotlin.math.abs(it)<=100_000f) {
                    "Non-finite or extreme vertex coordinate"
                }
            }
        }
    }
    private fun readIndices(root: JsonObject, primitive: JsonObject, vertexCount:Int,
                            binary:ByteArray, length:Int):IntArray {
        val id=primitive.get("indices")?.asInt
            ?: return IntArray(vertexCount){it}.also {
                require(it.size%3==0) {"Nonindexed mesh is not a triangle list"}
            }
        val a=arrays(root,"accessors").let {
            require(id in 0 until it.size()) {"Invalid index accessor"}
            it[id].asJsonObject
        }
        val type=a.get("componentType")?.asInt?:0
        val width=when(type){5121->1;5123->2;5125->4;else->
            throw IllegalArgumentException("Unsupported index component size")}
        require(a.get("type")?.asString=="SCALAR" && !a.has("sparse")) {
            "Indexed triangles must use a normal scalar accessor"
        }
        val count=a.get("count")?.asInt?:0
        require(count in 3..900_000 && count%3==0) {"Invalid triangle index count"}
        val views=arrays(root,"bufferViews")
        val viewId=a.get("bufferView")?.asInt?:-1
        require(viewId in 0 until views.size()) {"Index buffer view missing"}
        val v=views[viewId].asJsonObject
        require((v.get("buffer")?.asInt?:0)==0 && !v.has("extensions")) {
            "Compressed triangle indices are unsupported"
        }
        val start=v.get("byteOffset")?.asLong?:0
        val relative=a.get("byteOffset")?.asLong?:0
        val size=v.get("byteLength")?.asLong?:-1
        val stride=v.get("byteStride")?.asInt?:width
        val offset=start+relative
        require(start>=0 && relative>=0 && size>=0 && stride>=width &&
                stride<=256 && offset%width==0L &&
                offset+(count-1L)*stride+width<=min(start+size,length.toLong()) &&
                offset<=Int.MAX_VALUE) {"Index accessor exceeds GLB buffer"}
        val bb=ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(count){index ->
            val at=offset.toInt()+index*stride
            val value=when(width){
                1->bb.get(at).toInt() and 255
                2->bb.getShort(at).toInt() and 65535
                else->bb.getInt(at)
            }
            require(value in 0 until vertexCount) {"Triangle index references a missing vertex"}
            value
        }
    }

    private fun input(root:JsonObject,meshId:Int,primitiveId:Int,
                      bytes:ByteArray,length:Int):Input {
        val p=getPrimitive(root,meshId,primitiveId)
        val coords=readPositions(root,p,bytes,length)
        val indices=readIndices(root,p,coords.size/3,bytes,length)
        return Input(coords,indices,p)
    }

    fun surfaces(root:JsonObject,bytes:ByteArray,length:Int,nodeId:Int):List<Surface> {
        val meshId=nodeMesh(root,nodeId)
        val primitives=arrays(root,"meshes")[meshId].asJsonObject.getAsJsonArray("primitives")
            ?:return emptyList()
        return (0 until primitives.size()).map {primitiveId ->
            val selected=input(root,meshId,primitiveId,bytes,length)
            val minValues=FloatArray(3){Float.POSITIVE_INFINITY}
            val maxValues=FloatArray(3){Float.NEGATIVE_INFINITY}
            for(i in selected.positions.indices step 3)for(axis in 0..2){
                val v=selected.positions[i+axis]
                minValues[axis]=kotlin.math.min(minValues[axis],v)
                maxValues[axis]=kotlin.math.max(maxValues[axis],v)
            }
            Surface(primitiveId,selected.positions.size/3,minValues,maxValues)
        }
    }

    private fun appendFloatView(root:JsonObject,bytes:ByteBuffer,
                                numbers:FloatArray,start:Int):Int {
        val views=arrays(root,"bufferViews")
        val index=views.size()
        val view=JsonObject()
        view.addProperty("buffer",0)
        view.addProperty("byteOffset",start)
        view.addProperty("byteLength",numbers.size*4)
        view.addProperty("target",34962)
        views.add(view)
        bytes.position(start)
        numbers.forEach(bytes::putFloat)
        return index
    }
    private fun addVecAccessor(root:JsonObject,viewId:Int,count:Int,
                               minV:FloatArray?,maxV:FloatArray?):Int {
        val array=arrays(root,"accessors")
        val idx=array.size()
        val a=JsonObject()
        a.addProperty("bufferView",viewId)
        a.addProperty("componentType",5126)
        a.addProperty("count",count)
        a.addProperty("type","VEC3")
        if(minV!=null && maxV!=null) {
            val minJson=JsonArray();val maxJson=JsonArray()
            minV.forEach { minJson.add(it) };maxV.forEach { maxJson.add(it) }
            a.add("min",minJson);a.add("max",maxJson)
        }
        array.add(a)
        return idx
    }
    fun deform(source:JsonObject,binary:ByteArray,usedBytes:Int,
               nodeId:Int,brush:Brush):Result {
        require(brush.radius.isFinite() && brush.radius in 0.001f..1000f) {
            "Brush radius must be 0.001–1000 local metres"
        }
        val numbers=floatArrayOf(brush.centerX,brush.centerY,brush.centerZ,
            brush.moveX,brush.moveY,brush.moveZ)
        require(numbers.all {it.isFinite() && kotlin.math.abs(it)<=10000f}) {
            "Brush center or displacement is outside finite bounds"
        }
        require(kotlin.math.abs(brush.moveX)<=100f &&
                kotlin.math.abs(brush.moveY)<=100f &&
                kotlin.math.abs(brush.moveZ)<=100f &&
                (brush.moveX!=0f || brush.moveY!=0f || brush.moveZ!=0f)) {
            "Enter a nonzero local displacement within 100 m"
        }
        require(usedBytes in 0..binary.size) {"Invalid GLB buffer length"}
        // Work entirely on a private copy and only publish after successful encode().
        val root=source.deepCopy()
        var meshId=nodeMesh(root,nodeId)
        var target=input(root,meshId,brush.primitive,binary,usedBytes)
        val positions=target.positions.copyOf()
        val radiusSquared=brush.radius*brush.radius
        var affected=0
        for(i in positions.indices step 3) {
            val dx=positions[i]-brush.centerX
            val dy=positions[i+1]-brush.centerY
            val dz=positions[i+2]-brush.centerZ
            val d2=dx*dx+dy*dy+dz*dz
            if(d2>radiusSquared)continue
            val t=sqrt(max(0f,d2))/brush.radius
            val falloff=1f-t*t*(3f-2f*t) // smoothstep complement, continuous at edge
            if(falloff<=0f)continue
            val x=positions[i]+brush.moveX*falloff
            val y=positions[i+1]+brush.moveY*falloff
            val z=positions[i+2]+brush.moveZ*falloff
            require(x.isFinite() && y.isFinite() && z.isFinite()) {"Displacement overflow"}
            positions[i]=x;positions[i+1]=y;positions[i+2]=z
            affected++
        }
        require(affected>0) {"No vertices inside brush. Choose a larger radius or move its center."}
        // If multiple nodes share one mesh, copy that mesh for this node.
        // The second node must continue to render the original asset.
        val nodes=arrays(root,"nodes")
        val users=(0 until nodes.size()).count {i->
            nodes[i].asJsonObject.get("mesh")?.asInt==meshId
        }
        if(users>1) {
            val meshes=arrays(root,"meshes")
            val cloned=meshes[meshId].asJsonObject.deepCopy()
            meshes.add(cloned)
            meshId=meshes.size()-1
            nodes[nodeId].asJsonObject.addProperty("mesh",meshId)
            target=input(root,meshId,brush.primitive,binary,usedBytes)
        }
        // Recalculate area-weighted normals so the deformation shades correctly.
        // Split vertices remain split; UVs, tangents (rejected above), material,
        // skin, other primitives and original attribute buffers are not changed.
        val normals=FloatArray(positions.size)
        var nondegenerate=0
        for(i in target.indices.indices step 3) {
            val ai=target.indices[i]*3
            val bi=target.indices[i+1]*3
            val ci=target.indices[i+2]*3
            val ax=positions[bi]-positions[ai]
            val ay=positions[bi+1]-positions[ai+1]
            val az=positions[bi+2]-positions[ai+2]
            val bx=positions[ci]-positions[ai]
            val by=positions[ci+1]-positions[ai+1]
            val bz=positions[ci+2]-positions[ai+2]
            val nx=ay*bz-az*by
            val ny=az*bx-ax*bz
            val nz=ax*by-ay*bx
            val area2=nx*nx+ny*ny+nz*nz
            if(area2<1e-16f || !area2.isFinite())continue
            nondegenerate++
            for(v in intArrayOf(ai,bi,ci)) {
                normals[v]+=nx;normals[v+1]+=ny;normals[v+2]+=nz
            }
        }
        require(nondegenerate>0) {"Selected mesh contains no valid triangle normals"}
        for(i in normals.indices step 3){
            val len=sqrt(normals[i]*normals[i]+normals[i+1]*normals[i+1]+normals[i+2]*normals[i+2])
            if(len>1e-8f && len.isFinite()) {
                normals[i]/=len;normals[i+1]/=len;normals[i+2]/=len
            } else {
                // Degenerate/unused vertices get a finite safe normal.
                normals[i]=0f;normals[i+1]=1f;normals[i+2]=0f
            }
        }
        val lower=FloatArray(3){Float.POSITIVE_INFINITY}
        val upper=FloatArray(3){Float.NEGATIVE_INFINITY}
        for(i in positions.indices step 3)for(a in 0..2){
            lower[a]=min(lower[a],positions[i+a])
            upper[a]=max(upper[a],positions[i+a])
        }
        val padded=(usedBytes+3)and -4
        val bytesNeeded=positions.size.toLong()*8L
        val total=padded.toLong()+bytesNeeded
        require(total<=80L*1024*1024 && total<=Int.MAX_VALUE) {
            "Sculpt would exceed the safe GLB file limit"
        }
        val newBinary=ByteArray(total.toInt())
        binary.copyInto(newBinary,0,0,usedBytes)
        val writer=ByteBuffer.wrap(newBinary).order(ByteOrder.LITTLE_ENDIAN)
        val positionView=appendFloatView(root,writer,positions,padded)
        val normalView=appendFloatView(root,writer,normals,padded+positions.size*4)
        val posAccessor=addVecAccessor(root,positionView,positions.size/3,lower,upper)
        val normalAccessor=addVecAccessor(root,normalView,positions.size/3,null,null)
        val editedPrimitive=getPrimitive(root,meshId,brush.primitive)
        editedPrimitive.getAsJsonObject("attributes").apply {
            addProperty("POSITION",posAccessor)
            addProperty("NORMAL",normalAccessor)
        }
        arrays(root,"buffers")[0].asJsonObject.addProperty("byteLength",newBinary.size)
        return Result(root,newBinary,newBinary.size)
    }
}
