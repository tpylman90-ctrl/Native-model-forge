package com.modelforge.nativeeditor

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class GlbWorkshopTest {
    private fun fixture(extra: String = ""): ByteArray {
        val source = """
            {
              "asset":{"version":"2.0"},
              "scene":0,
              "scenes":[{"nodes":[0,1]}],
              "nodes":[
                {"name":"FRONT_SPLITTER_CANARD","mesh":0},
                {"name":"REAR_THRUSTER_POD","mesh":0,
                 "translation":[1,0,0],"scale":[1,1,1]}
              ],
              "meshes":[{"name":"base","primitives":[{"attributes":{"POSITION":0}}]}],
              "buffers":[{"byteLength":16}],
              "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":16}],
              "accessors":[{"bufferView":0,"componentType":5126,"count":1,
                "type":"VEC3","min":[-1,-1,-1],"max":[1,1,1]}],
              "materials":[{"name":"Original Metallic Paint"}],
              "extras":{"factory":"preserve-original"}, 
              "extensionsUsed":["KHR_materials_clearcoat"]
              ${if(extra.isNotBlank()) ",$extra" else ""}
            }
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        val pad = (4 - source.size%4)%4
        val out=ByteBuffer.allocate(12+8+source.size+pad+8+16).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546c67);out.putInt(2);out.putInt(out.capacity())
        out.putInt(source.size+pad);out.putInt(0x4e4f534a);out.put(source)
        repeat(pad) {out.put(32.toByte())}
        out.putInt(16);out.putInt(0x004e4942)
        repeat(16) {out.put(it.toByte())}
        return out.array()
    }
    private fun json(bytes:ByteArray):com.google.gson.JsonObject {
        val data=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546c67,data.int)
        assertEquals(2,data.int)
        assertEquals(bytes.size,data.int)
        val size=data.int
        assertEquals(0x4e4f534a,data.int)
        val encoded=ByteArray(size);data.get(encoded)
        return JsonParser.parseString(
            String(encoded,StandardCharsets.UTF_8).trimEnd(' ','\u0000')
        ).asJsonObject
    }

    @Test fun loadedCraftPartsAreSearchableAndOriginalDataRemains() {
        val bytes=fixture()
        val project=GlbWorkshop.open(bytes)
        assertEquals(2,project.parts().size)
        assertEquals("FRONT_SPLITTER_CANARD",project.parts()[0].label)
        assertEquals("REAR_THRUSTER_POD",project.parts()[1].label)
        assertEquals("preserve-original",json(project.export()).getAsJsonObject("extras").get("factory").asString)
        assertEquals("Original Metallic Paint",json(project.export())
            .getAsJsonArray("materials")[0].asJsonObject.get("name").asString)
        assertArrayEquals(binaryTail(bytes),binaryTail(project.export()))
    }

    @Test fun previewIsNonDestructiveAndApplyingTransformCanBeUndoneAndRedone() {
        val project=GlbWorkshop.open(fixture())
        val baseline=project.export()
        val delta=GlbWorkshop.Delta(moveX=0.5f,moveY=0.25f,turnY=90f,
            scaleX=1.2f,scaleY=1.1f,scaleZ=0.8f)
        val preview=project.previewPart(0,delta)
        assertNotEquals(baseline.toList(),preview.toList())
        assertArrayEquals(baseline,project.export())
        val updated=project.applyPart(0,delta)
        val node=json(updated).getAsJsonArray("nodes")[0].asJsonObject
        assertTrue(node.has("matrix"))
        assertFalse(node.has("translation"))
        assertEquals(16,node.getAsJsonArray("matrix").size())
        assertEquals(0.5f,node.getAsJsonArray("matrix")[12].asFloat,0.0001f)
        assertTrue(project.canUndo)
        assertArrayEquals(baseline,project.undo())
        assertArrayEquals(updated,project.redo())
    }

    @Test fun rotatingAndResizingPreservesAnotherNodesTransform() {
        val project=GlbWorkshop.open(fixture())
        val existing=json(project.export()).getAsJsonArray("nodes")[1].toString()
        val moved=project.applyPart(0,GlbWorkshop.Delta(scaleZ=2f,turnX=22f))
        assertEquals(existing,json(moved).getAsJsonArray("nodes")[1].toString())
    }

    @Test fun newPanelIsRealGeometryAndPreservesOriginalChunks() {
        val project=GlbWorkshop.open(fixture())
        val packed=FloatArray(216) {i ->
            when(i%6) { 0->(i/6%3).toFloat();1->0.25f;2->1f;3->0f;4->1f;else->0f }
        }
        val material=floatArrayOf(0.2f,0.5f,0.7f,1f)
        val prior=project.export()
        val preview=project.previewNewPart("Left Armor Panel",packed,material)
        assertArrayEquals(prior,project.export())
        assertEquals(3,json(preview).getAsJsonArray("nodes").size())
        val result=project.addPart("Left Armor Panel",packed,material)
        assertEquals(3,project.parts().size)
        val root=json(result)
        assertEquals(2,root.getAsJsonArray("meshes").size())
        assertEquals(2,root.getAsJsonArray("materials").size())
        assertEquals("Original Metallic Paint",root.getAsJsonArray("materials")[0]
            .asJsonObject.get("name").asString)
        assertEquals(880,root.getAsJsonArray("buffers")[0].asJsonObject.get("byteLength").asInt)
        assertEquals(2,root.getAsJsonArray("scenes")[0].asJsonObject
            .getAsJsonArray("nodes")[2].asInt)
        assertEquals(3,GlbWorkshop.open(result).parts().size)
        assertArrayEquals(prior,project.undo())
        assertArrayEquals(result,project.redo())
    }

    @Test fun animatedNodesAndOutOfRangeTransformsAreProtected() {
        val animated = "\"animations\":[{\"channels\":[{\"target\":{\"node\":1,\"path\":\"translation\"}}]}]"
        val project=GlbWorkshop.open(fixture(animated))
        assertEquals(1,project.parts().size)
        try {
            project.previewPart(1,GlbWorkshop.Delta(moveX=1f))
            fail("Expected animated node edit rejection")
        } catch(_:IllegalArgumentException) { }
        try {
            project.previewPart(0,GlbWorkshop.Delta(scaleX=-1f))
            fail("Expected invalid scale rejection")
        } catch(_:IllegalArgumentException) { }
    }

    @Test fun malformedGlbAndUnsafeBufferTypesAreRejected() {
        try {
            GlbWorkshop.open(byteArrayOf(0,1,2))
            fail("Expected invalid GLB")
        } catch(_:IllegalArgumentException) { }
        val broken=fixture().copyOf()
        ByteBuffer.wrap(broken).order(ByteOrder.LITTLE_ENDIAN).putInt(8,broken.size+8)
        try {
            GlbWorkshop.open(broken)
            fail("Expected invalid declared size")
        } catch(_:IllegalArgumentException) { }
    }

    private fun binaryTail(bytes:ByteArray):ByteArray {
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.position(12);val jsonLength=b.int
        b.int;b.position(b.position()+jsonLength)
        val length=b.int;b.int
        return ByteArray(length).also {b.get(it)}
    }
}
