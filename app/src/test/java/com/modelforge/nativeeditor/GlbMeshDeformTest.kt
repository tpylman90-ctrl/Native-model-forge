package com.modelforge.nativeeditor

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class GlbMeshDeformTest {
    private val partColor=floatArrayOf(0.14f,0.45f,0.64f,1f)
    private val triangle=floatArrayOf(
        0f,0f,0f, 0f,0f,1f,
        1f,0f,0f, 0f,0f,1f,
        0f,1f,0f, 0f,0f,1f
    )
    private fun model():ByteArray=GlbWorkshop.blank()
        .addPart("Imported front fender",triangle,partColor)

    private fun json(bytes:ByteArray):JsonObject {
        val bb=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546c67,bb.int);assertEquals(2,bb.int)
        assertEquals(bytes.size,bb.int)
        val jsonSize=bb.int
        assertEquals(0x4e4f534a,bb.int)
        val encoded=ByteArray(jsonSize);bb.get(encoded)
        return JsonParser.parseString(String(encoded,StandardCharsets.UTF_8)
            .trimEnd(' ','\u0000')).asJsonObject
    }
    private fun buffer(bytes:ByteArray):ByteBuffer {
        val bb=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(12);val jsonSize=bb.int
        bb.int;bb.position(bb.position()+jsonSize)
        val length=bb.int;assertEquals(0x004e4942,bb.int)
        val data=ByteArray(length);bb.get(data)
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    }
    private fun vertex(bytes:ByteArray,node:Int,index:Int):FloatArray {
        val root=json(bytes)
        val meshId=root.getAsJsonArray("nodes")[node].asJsonObject.get("mesh").asInt
        val primitive=root.getAsJsonArray("meshes")[meshId].asJsonObject
            .getAsJsonArray("primitives")[0].asJsonObject
        val accessorId=primitive.getAsJsonObject("attributes").get("POSITION").asInt
        val accessor=root.getAsJsonArray("accessors")[accessorId].asJsonObject
        val view=root.getAsJsonArray("bufferViews")[accessor.get("bufferView").asInt].asJsonObject
        val stride=view.get("byteStride")?.asInt?:12
        val offset=(view.get("byteOffset")?.asInt?:0)+(accessor.get("byteOffset")?.asInt?:0)
            +index*stride
        val bin=buffer(bytes)
        return floatArrayOf(bin.getFloat(offset),bin.getFloat(offset+4),bin.getFloat(offset+8))
    }
    private fun duplicateNode(bytes:ByteArray):ByteArray {
        val root=json(bytes)
        val nodes=root.getAsJsonArray("nodes")
        val twin=JsonObject()
        twin.addProperty("name","Mirrored mesh instance")
        twin.addProperty("mesh",nodes[0].asJsonObject.get("mesh").asInt)
        nodes.add(twin)
        root.getAsJsonArray("scenes")[0].asJsonObject.getAsJsonArray("nodes").add(1)
        val payload=buffer(bytes).array()
        val source=root.toString().toByteArray(StandardCharsets.UTF_8)
        val jsonPad=(4-source.size%4)%4
        val binPad=(4-payload.size%4)%4
        val total=12+8+source.size+jsonPad+8+payload.size+binPad
        val bb=ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(0x46546c67);bb.putInt(2);bb.putInt(total)
        bb.putInt(source.size+jsonPad);bb.putInt(0x4e4f534a);bb.put(source)
        repeat(jsonPad){bb.put(32.toByte())}
        bb.putInt(payload.size+binPad);bb.putInt(0x004e4942);bb.put(payload)
        repeat(binPad){bb.put(0)}
        return bb.array()
    }

    @Test fun importedMeshBrushMovesVerticesNotWholePart() {
        val source=model()
        val workshop=GlbWorkshop.open(source)
        val node=workshop.parts().single().node
        val surface=workshop.meshSurfaces(node).single()
        assertEquals(0,surface.primitive)
        assertEquals(3,surface.vertices)
        assertArrayEquals(floatArrayOf(0f,0f,0f),surface.min,0f)
        assertArrayEquals(floatArrayOf(1f,1f,0f),surface.max,0f)
        val brush=GlbWorkshop.MeshBrush(0,0f,0f,0f,.3f,0f,0f,.2f)
        val preview=workshop.previewMeshBrush(node,brush)
        assertArrayEquals(source,workshop.export())
        assertEquals(.2f,vertex(preview,0,0)[2],.00001f)
        assertEquals(0f,vertex(preview,0,1)[2],.00001f)
        assertEquals(0f,vertex(preview,0,2)[2],.00001f)
        val saved=workshop.applyMeshBrush(node,brush)
        assertArrayEquals(preview,saved)
        assertEquals(3,workshop.meshSurfaces(node).single().vertices)
        assertEquals("Imported front fender",workshop.parts().single().label)
        val original=json(source)
        val edited=json(saved)
        assertEquals(original.getAsJsonArray("materials").toString(),
            edited.getAsJsonArray("materials").toString())
        assertEquals(original.getAsJsonArray("scenes").toString(),
            edited.getAsJsonArray("scenes").toString())
        val originalBinary=buffer(source).array()
        val editedBinary=buffer(saved).array()
        assertTrue(editedBinary.size>originalBinary.size)
        assertArrayEquals(originalBinary,editedBinary.copyOf(originalBinary.size))
        assertArrayEquals(source,workshop.undo())
        assertArrayEquals(saved,workshop.redo())
        val reopened=GlbWorkshop.open(saved)
        assertEquals(.2f,vertex(reopened.export(),0,0)[2],.00001f)
    }

    @Test fun twoNodesSharingOneMeshStayIndependent() {
        val shared=duplicateNode(model())
        val workshop=GlbWorkshop.open(shared)
        assertEquals(2,workshop.parts().size)
        val originalVertex=vertex(shared,0,0)
        val brush=GlbWorkshop.MeshBrush(0,0f,0f,0f,.3f,.4f,0f,0f)
        val output=workshop.applyMeshBrush(1,brush)
        val doc=json(output)
        val nodes=doc.getAsJsonArray("nodes")
        assertNotEquals(nodes[0].asJsonObject.get("mesh").asInt,
            nodes[1].asJsonObject.get("mesh").asInt)
        assertArrayEquals(originalVertex,vertex(output,0,0),0f)
        assertEquals(.4f,vertex(output,1,0)[0],.00001f)
        assertArrayEquals(shared,workshop.undo())
        assertArrayEquals(output,workshop.redo())
    }

    @Test fun invalidAndNoHitEditsAreAtomic() {
        val workshop=GlbWorkshop.open(model())
        val source=workshop.export()
        val invalid=listOf(
            GlbWorkshop.MeshBrush(0,0f,0f,0f,-1f,.2f,0f,0f),
            GlbWorkshop.MeshBrush(0,50f,50f,50f,.2f,.2f,0f,0f),
            GlbWorkshop.MeshBrush(0,0f,0f,0f,.5f,0f,0f,0f),
            GlbWorkshop.MeshBrush(10,0f,0f,0f,.5f,.2f,0f,0f),
            GlbWorkshop.MeshBrush(0,Float.NaN,0f,0f,.5f,.2f,0f,0f)
        )
        for(brush in invalid) {
            try{workshop.applyMeshBrush(0,brush);fail("Expected mesh edit rejection")}
            catch(_:IllegalArgumentException){}
            assertArrayEquals(source,workshop.export())
            assertFalse(workshop.canUndo)
        }
    }

    @Test fun weightedMoveSmoothlyFadesAtBrushBoundary() {
        val workshop=GlbWorkshop.open(model())
        val brush=GlbWorkshop.MeshBrush(0,0f,0f,0f,1.5f,0f,0f,.5f)
        val edited=workshop.applyMeshBrush(0,brush)
        val full=vertex(edited,0,0)[2]
        val edge=vertex(edited,0,1)[2]
        assertEquals(.5f,full,.00001f)
        assertTrue(edge>0f && edge<full)
        assertEquals(edge,vertex(edited,0,2)[2],.000001f)
    }
}
