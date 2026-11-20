package com.modelforge.nativeeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Safe, lossless-for-mesh-data glTF 2.0 GLB component workshop.
 *
 * Original indices, vertices, buffer views, textures, materials, hierarchy,
 * skins and extensions are never rewritten. Only a selected unanimated mesh
 * node's transform is changed, or a new independent mesh node is appended.
 *
 * This is a component editor, NOT arbitrary surface/vertex sculpting.
 * Its compact JSON checkpoints make imported GLB undo possible without copying
 * tens of megabytes of geometry on every touch adjustment.
 */
class GlbWorkshop private constructor(
    private var document: JsonObject,
    private var binary: ByteArray,
    private var usedBytes: Int
) {
    data class Part(val node: Int, val label: String, val mesh: Int)
    data class Delta(
        val moveX: Float = 0f, val moveY: Float = 0f, val moveZ: Float = 0f,
        val turnX: Float = 0f, val turnY: Float = 0f, val turnZ: Float = 0f,
        val scaleX: Float = 1f, val scaleY: Float = 1f, val scaleZ: Float = 1f
    )
    data class Starter(val name: String, val width: Float, val height: Float,
                       val length: Float, val topWidth: Float, val topLength: Float,
                       val color: FloatArray)
    private data class Checkpoint(val json: JsonObject, val length: Int)

    private val undoHistory = ArrayDeque<Checkpoint>()
    private val redoHistory = ArrayDeque<Checkpoint>()
    private val maximumHistory = if(document.toString().length > 750_000) 4 else 16

    val canUndo: Boolean get() = undoHistory.isNotEmpty()
    val canRedo: Boolean get() = redoHistory.isNotEmpty()

    companion object {
        val starters = listOf(
            Starter("Armor panel",1.35f,0.14f,1.8f,1f,1f,
                floatArrayOf(0.24f,0.38f,0.53f,1f)),
            Starter("Thruster pod",0.75f,0.65f,1.65f,0.88f,0.9f,
                floatArrayOf(0.22f,0.28f,0.35f,1f)),
            Starter("Aero fin",0.14f,0.85f,1.35f,0.7f,0.7f,
                floatArrayOf(0.15f,0.7f,0.75f,1f)),
            Starter("Front intake block",1.0f,0.38f,0.7f,0.82f,0.86f,
                floatArrayOf(0.12f,0.16f,0.22f,1f)),
            Starter("Body fairing blank",1.4f,0.5f,2.1f,0.7f,0.76f,
                floatArrayOf(0.36f,0.47f,0.59f,1f))
        )
        fun open(bytes: ByteArray): GlbWorkshop {
            require(bytes.size in 28..(80*1024*1024)) { "GLB size is outside the phone editing limit" }
            val file=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(file.int==0x46546C67 && file.int==2 && file.int==bytes.size) {
                "File is not a glTF 2.0 binary model"
            }
            val jsonLength=file.int
            require(jsonLength>0 && jsonLength<=12*1024*1024 &&
                jsonLength%4==0 && jsonLength<=file.remaining()-4) { "Invalid GLB JSON length" }
            require(file.int==0x4e4f534a) { "GLB JSON chunk missing" }
            val jsonBytes=ByteArray(jsonLength)
            file.get(jsonBytes)
            val root=JsonParser.parseString(
                String(jsonBytes,StandardCharsets.UTF_8).trimEnd(' ','\n','\r','\t','\u0000')
            ).asJsonObject
            val bin=if(file.hasRemaining()) {
                require(file.remaining()>=8) { "Incomplete GLB binary chunk" }
                val length=file.int
                require(length>=0 && length%4==0 && file.int==0x004e4942 &&
                    length==file.remaining()) { "Unsupported or malformed GLB binary chunk" }
                ByteArray(length).also {file.get(it)}
            } else byteArrayOf()
            val buffers=root.getAsJsonArray("buffers")
                ?: throw IllegalArgumentException("Missing glTF binary buffer")
            require(buffers.size()==1 && !buffers[0].asJsonObject.has("uri")) {
                "This GLB uses external or multiple buffers; it can still be viewed"
            }
            val declared=buffers[0].asJsonObject.get("byteLength").asInt
            require(declared>=0 && declared<=bin.size) { "GLB buffer length mismatch" }
            require(root.getAsJsonArray("nodes")!=null &&
                    root.getAsJsonArray("meshes")!=null &&
                    root.getAsJsonArray("scenes")!=null) {
                "Mesh nodes or scenes missing from GLB"
            }
            val editor=GlbWorkshop(root,bin,declared)
            require(editor.parts().isNotEmpty()) {
                "No safe independent mesh components found; preview remains available"
            }
            return editor
        }
        private fun numberArray(values: FloatArray):JsonArray =
            JsonArray().apply { values.forEach {add(it)} }
        private fun identity():FloatArray = FloatArray(16) { if(it%5==0) 1f else 0f }
        private fun multiply(a:FloatArray,b:FloatArray):FloatArray {
            val o=FloatArray(16)
            for(c in 0..3) for(r in 0..3) for(k in 0..3)
                o[c*4+r]+=a[k*4+r]*b[c*4+k]
            return o
        }
        private fun translate(x:Float,y:Float,z:Float):FloatArray =
            identity().apply {this[12]=x;this[13]=y;this[14]=z}
        private fun scaling(x:Float,y:Float,z:Float):FloatArray =
            identity().apply {this[0]=x;this[5]=y;this[10]=z}
        private fun rotX(angle:Float):FloatArray {
            val a=Math.toRadians(angle.toDouble()); val c=cos(a).toFloat();val s=sin(a).toFloat()
            return identity().apply {this[5]=c;this[6]=s;this[9]=-s;this[10]=c}
        }
        private fun rotY(angle:Float):FloatArray {
            val a=Math.toRadians(angle.toDouble()); val c=cos(a).toFloat();val s=sin(a).toFloat()
            return identity().apply {this[0]=c;this[2]=-s;this[8]=s;this[10]=c}
        }
        private fun rotZ(angle:Float):FloatArray {
            val a=Math.toRadians(angle.toDouble()); val c=cos(a).toFloat();val s=sin(a).toFloat()
            return identity().apply {this[0]=c;this[1]=s;this[4]=-s;this[5]=c}
        }
        private fun getFloats(json:JsonArray?,expected:Int,defaults:FloatArray):FloatArray {
            if(json==null)return defaults.copyOf()
            require(json.size()==expected) {"Invalid node transform array"}
            return FloatArray(expected) { json[it].asFloat.also {value ->
                require(value.isFinite()){"Non-finite transform value"}
            }}
        }
        private fun currentMatrix(node:JsonObject):FloatArray {
            node.getAsJsonArray("matrix")?.let {
                return getFloats(it,16,identity())
            }
            val translation=getFloats(node.getAsJsonArray("translation"),3,floatArrayOf(0f,0f,0f))
            val scale=getFloats(node.getAsJsonArray("scale"),3,floatArrayOf(1f,1f,1f))
            val q=getFloats(node.getAsJsonArray("rotation"),4,floatArrayOf(0f,0f,0f,1f))
            val norm=kotlin.math.sqrt(q.sumOf { (it*it).toDouble() }).toFloat()
            require(norm>0.00001f) { "Invalid node quaternion" }
            val x=q[0]/norm;val y=q[1]/norm;val z=q[2]/norm;val w=q[3]/norm
            val rotation=identity().apply {
                this[0]=1f-2f*(y*y+z*z);this[1]=2f*(x*y+z*w);this[2]=2f*(x*z-y*w)
                this[4]=2f*(x*y-z*w);this[5]=1f-2f*(x*x+z*z);this[6]=2f*(y*z+x*w)
                this[8]=2f*(x*z+y*w);this[9]=2f*(y*z-x*w);this[10]=1f-2f*(x*x+y*y)
            }
            return multiply(multiply(translate(translation[0],translation[1],translation[2]),rotation),
                scaling(scale[0],scale[1],scale[2]))
        }
    }

    fun parts():List<Part> {
        val nodes=document.getAsJsonArray("nodes") ?: return emptyList()
        val meshes=document.getAsJsonArray("meshes") ?: return emptyList()
        val scenes=document.getAsJsonArray("scenes") ?: return emptyList()
        val sceneIndex=document.get("scene")?.asInt ?: 0
        if(sceneIndex !in 0 until scenes.size())return emptyList()
        val animated=HashSet<Int>()
        document.getAsJsonArray("animations")?.forEach { anim ->
            anim.asJsonObject.getAsJsonArray("channels")?.forEach { channel ->
                channel.asJsonObject.getAsJsonObject("target")?.get("node")?.let {
                    animated.add(it.asInt)
                }
            }
        }
        val visited=HashSet<Int>()
        val found=ArrayList<Part>()
        fun walk(index:Int,depth:Int) {
            if(index !in 0 until nodes.size() || depth>64 || !visited.add(index))return
            val node=nodes[index].asJsonObject
            if(node.has("mesh") && !node.has("skin") && index !in animated &&
                !node.getAsJsonObject("extensions").let { it?.has("EXT_mesh_gpu_instancing")?:false }) {
                val meshIndex=node.get("mesh").asInt
                if(meshIndex in 0 until meshes.size()) {
                    val mesh=meshes[meshIndex].asJsonObject
                    val name=node.get("name")?.asString?.takeIf {it.isNotBlank()}
                        ?: mesh.get("name")?.asString?.takeIf {it.isNotBlank()}
                        ?: "Mesh part ${index+1}"
                    found.add(Part(index,name.take(120),meshIndex))
                }
            }
            node.getAsJsonArray("children")?.forEach { walk(it.asInt,depth+1) }
        }
        scenes[sceneIndex].asJsonObject.getAsJsonArray("nodes")?.forEach {walk(it.asInt,0)}
        return found.take(3000)
    }

    private fun pivot(meshIndex:Int,root:JsonObject):FloatArray {
        val meshes=root.getAsJsonArray("meshes")
        val accessors=root.getAsJsonArray("accessors")?:return floatArrayOf(0f,0f,0f)
        if(meshIndex !in 0 until meshes.size())return floatArrayOf(0f,0f,0f)
        val min=FloatArray(3){Float.POSITIVE_INFINITY}
        val max=FloatArray(3){Float.NEGATIVE_INFINITY}
        meshes[meshIndex].asJsonObject.getAsJsonArray("primitives")?.forEach {primitive ->
            val index=primitive.asJsonObject.getAsJsonObject("attributes")?.get("POSITION")?.asInt
                ?:return@forEach
            if(index !in 0 until accessors.size())return@forEach
            val accessor=accessors[index].asJsonObject
            val lows=accessor.getAsJsonArray("min")?:return@forEach
            val highs=accessor.getAsJsonArray("max")?:return@forEach
            if(lows.size()!=3||highs.size()!=3)return@forEach
            for(axis in 0..2) {
                min[axis]=kotlin.math.min(min[axis],lows[axis].asFloat)
                max[axis]=kotlin.math.max(max[axis],highs[axis].asFloat)
            }
        }
        return FloatArray(3) { if(min[it].isFinite() && max[it].isFinite())
            (min[it]+max[it])*0.5f else 0f }
    }

    private fun validate(delta:Delta) {
        for(v in floatArrayOf(delta.moveX,delta.moveY,delta.moveZ,
                            delta.turnX,delta.turnY,delta.turnZ)) {
            require(v.isFinite()&&abs(v)<=1000f) { "Move/turn amount is outside safe limits" }
        }
        for(v in floatArrayOf(delta.scaleX,delta.scaleY,delta.scaleZ)) {
            require(v.isFinite()&&v in 0.1f..5f) {
                "Size multiplier must be 0.10 to 5.00"
            }
        }
    }

    private fun applyDelta(root:JsonObject,index:Int,delta:Delta) {
        validate(delta)
        val part=parts().firstOrNull { it.node==index }
            ?:throw IllegalArgumentException("This mesh node is not an editable component")
        val node=root.getAsJsonArray("nodes")[index].asJsonObject
        val base=currentMatrix(node)
        val pivot=pivot(part.mesh,root)
        val turn=multiply(multiply(rotZ(delta.turnZ),rotY(delta.turnY)),rotX(delta.turnX))
        val change=multiply(turn,scaling(delta.scaleX,delta.scaleY,delta.scaleZ))
        val local=multiply(multiply(translate(pivot[0],pivot[1],pivot[2]),change),
            translate(-pivot[0],-pivot[1],-pivot[2]))
        val transformed=multiply(multiply(translate(delta.moveX,delta.moveY,delta.moveZ),base),local)
        require(transformed.all {it.isFinite()}) { "Invalid component transformation" }
        node.remove("translation");node.remove("rotation");node.remove("scale")
        node.add("matrix",numberArray(transformed))
    }

    fun previewPart(index:Int,delta:Delta):ByteArray {
        val copy=document.deepCopy()
        applyDelta(copy,index,delta)
        return encode(copy,binary,usedBytes)
    }

    fun applyPart(index:Int,delta:Delta):ByteArray {
        val next=document.deepCopy()
        applyDelta(next,index,delta)
        val output=encode(next,binary,usedBytes)
        pushCheckpoint()
        document=next
        return output
    }

    private fun pushCheckpoint() {
        undoHistory.addLast(Checkpoint(document.deepCopy(),usedBytes))
        while(undoHistory.size>maximumHistory)undoHistory.removeFirst()
        redoHistory.clear()
    }

    /** Adds a real independent glTF mesh node using generated C++ triangles.
     * Existing buffer views, materials, images and mesh data are preserved. */
    fun addPart(name:String,packed:FloatArray,color:FloatArray):ByteArray {
        require(name.length in 1..80) { "Name must be 1 to 80 characters" }
        require(packed.size>=216 && packed.size%18==0 && packed.size<=600_000 &&
                packed.all {it.isFinite()}) { "Generated mesh has invalid vertices" }
        require(color.size==4 && color.all {it.isFinite()&&it in 0f..1f}) {
            "Invalid material color"
        }
        val root=document.deepCopy()
        val oldBytes=usedBytes
        val padded=(oldBytes+3)and -4
        val data=ByteBuffer.allocate(packed.size*4).order(ByteOrder.LITTLE_ENDIAN)
        packed.forEach {data.putFloat(it)}
        val updated=ByteArray(padded+data.capacity())
        binary.copyInto(updated,0,0,usedBytes)
        data.array().copyInto(updated,padded)
        val bufferViews=root.getAsJsonArray("bufferViews")?:JsonArray().also {
            root.add("bufferViews",it)
        }
        val view=bufferViews.size()
        bufferViews.add(JsonObject().apply {
            addProperty("buffer",0);addProperty("byteOffset",padded)
            addProperty("byteLength",data.capacity());addProperty("byteStride",24)
            addProperty("target",34962)
        })
        val min=FloatArray(3){Float.POSITIVE_INFINITY}
        val max=FloatArray(3){Float.NEGATIVE_INFINITY}
        for(i in packed.indices step 6)for(axis in 0..2) {
            min[axis]=kotlin.math.min(min[axis],packed[i+axis])
            max[axis]=kotlin.math.max(max[axis],packed[i+axis])
        }
        val accessors=root.getAsJsonArray("accessors")?:JsonArray().also {
            root.add("accessors",it)
        }
        val pos=accessors.size()
        accessors.add(JsonObject().apply {
            addProperty("bufferView",view);addProperty("componentType",5126)
            addProperty("count",packed.size/6);addProperty("type","VEC3")
            add("min",numberArray(min));add("max",numberArray(max))
        })
        val normal=accessors.size()
        accessors.add(JsonObject().apply {
            addProperty("bufferView",view);addProperty("byteOffset",12)
            addProperty("componentType",5126);addProperty("count",packed.size/6)
            addProperty("type","VEC3")
        })
        val materials=root.getAsJsonArray("materials")?:JsonArray().also {
            root.add("materials",it)
        }
        val material=materials.size()
        materials.add(JsonObject().apply {
            addProperty("name","Model Forge | "+name)
            addProperty("doubleSided",true)
            add("pbrMetallicRoughness",JsonObject().apply {
                add("baseColorFactor",numberArray(color))
                addProperty("metallicFactor",0.35)
                addProperty("roughnessFactor",0.46)
            })
        })
        val meshes=root.getAsJsonArray("meshes")
        val mesh=meshes.size()
        meshes.add(JsonObject().apply {
            addProperty("name",name)
            add("primitives",JsonArray().apply {
                add(JsonObject().apply {
                    add("attributes",JsonObject().apply {
                        addProperty("POSITION",pos);addProperty("NORMAL",normal)
                    })
                    addProperty("material",material);addProperty("mode",4)
                })
            })
        })
        val nodes=root.getAsJsonArray("nodes")
        val node=nodes.size()
        nodes.add(JsonObject().apply {
            addProperty("mesh",mesh);addProperty("name",name)
        })
        val activeScene=root.get("scene")?.asInt?:0
        val scene=root.getAsJsonArray("scenes")[activeScene].asJsonObject
        val roots=scene.getAsJsonArray("nodes")?:JsonArray().also {
            scene.add("nodes",it)
        }
        roots.add(node)
        root.getAsJsonArray("buffers")[0].asJsonObject
            .addProperty("byteLength",updated.size)
        val output=encode(root,updated,updated.size)
        pushCheckpoint()
        binary=updated
        usedBytes=updated.size
        document=root
        return output
    }

    fun undo():ByteArray? {
        if(undoHistory.isEmpty())return null
        redoHistory.addLast(Checkpoint(document.deepCopy(),usedBytes))
        val checkpoint=undoHistory.removeLast()
        document=checkpoint.json
        usedBytes=checkpoint.length
        return export()
    }

    fun redo():ByteArray? {
        if(redoHistory.isEmpty())return null
        undoHistory.addLast(Checkpoint(document.deepCopy(),usedBytes))
        val checkpoint=redoHistory.removeLast()
        document=checkpoint.json
        usedBytes=checkpoint.length
        return export()
    }

    fun export():ByteArray=encode(document,binary,usedBytes)

    private fun encode(root:JsonObject,buffer:ByteArray,length:Int):ByteArray {
        require(length in 0..buffer.size) { "GLB binary state out of range" }
        val json=root.toString().toByteArray(StandardCharsets.UTF_8)
        val jsonPad=(4-json.size%4)%4
        val binPad=(4-length%4)%4
        val total=12L+8+json.size+jsonPad+8+length+binPad
        require(total<=80L*1024*1024 && total<=Int.MAX_VALUE) {
            "Edited GLB exceeds safe 80 MB limit"
        }
        val out=ByteBuffer.allocate(total.toInt()).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67);out.putInt(2);out.putInt(total.toInt())
        out.putInt(json.size+jsonPad);out.putInt(0x4e4f534a);out.put(json)
        repeat(jsonPad){out.put(0x20.toByte())}
        out.putInt(length+binPad);out.putInt(0x004e4942)
        out.put(buffer,0,length)
        repeat(binPad){out.put(0)}
        return out.array()
    }
}
