package com.modelforge.nativeeditor

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Foundation-only GLB encoder. C++ returns 36 triangle vertices with positions
 * and normals; this serializes that actual mesh to portable glTF 2.0.
 * It does NOT pretend to modify arbitrary imported GLB topology.
 */
object GlbBuilder {
    fun box(width: Float, height: Float, length: Float): ByteArray {
        val packed = NativeGeometry.generateBox(width, height, length)
        require(packed.size == 36 * 6) { "Native box kernel produced invalid triangle layout" }
        val bin = ByteBuffer.allocate(packed.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (number in packed) bin.putFloat(number)
        val w = fmt(width / 2f)
        val h = fmt(height / 2f)
        val l = fmt(length / 2f)
        val json = """{
          "asset":{"version":"2.0","generator":"Model Forge Native 0.1 / C++ mesh kernel"},
          "scene":0,"scenes":[{"nodes":[0]}],
          "nodes":[{"mesh":0,"name":"Starter Box"}],
          "meshes":[{"name":"Starter Box","primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"material":0,"mode":4}]}],
          "materials":[{"name":"Forge Satin Steel","doubleSided":true,"pbrMetallicRoughness":{"baseColorFactor":[0.26,0.57,0.66,1.0],"metallicFactor":0.48,"roughnessFactor":0.38}}],
          "buffers":[{"byteLength":@LEN@}],
          "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":@LEN@,"byteStride":24,"target":34962}],
          "accessors":[
            {"bufferView":0,"byteOffset":0,"componentType":5126,"count":36,"type":"VEC3","min":[-@W@,-@H@,-@L@],"max":[@W@,@H@,@L@]},
            {"bufferView":0,"byteOffset":12,"componentType":5126,"count":36,"type":"VEC3"}
          ]
        }""".trimIndent()
            .replace("@LEN@", bin.capacity().toString())
            .replace("@W@", w).replace("@H@", h).replace("@L@", l)
            .toByteArray(StandardCharsets.UTF_8)
        val jsonPad = (4 - json.size % 4) % 4
        val binPad = (4 - bin.capacity() % 4) % 4
        val total = 12 + 8 + json.size + jsonPad + 8 + bin.capacity() + binPad
        val bytes = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        bytes.putInt(0x46546C67) // glTF
        bytes.putInt(2)
        bytes.putInt(total)
        bytes.putInt(json.size + jsonPad)
        bytes.putInt(0x4E4F534A) // JSON
        bytes.put(json)
        repeat(jsonPad) { bytes.put(0x20.toByte()) }
        bytes.putInt(bin.capacity() + binPad)
        bytes.putInt(0x004E4942) // BIN
        bytes.put(bin.array())
        repeat(binPad) { bytes.put(0) }
        return bytes.array()
    }

    private fun fmt(number: Float): String = String.format(Locale.US, "%.5f", number)

    fun isValidHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 20 || bytes.size > 90 * 1024 * 1024) return false
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return header.int == 0x46546C67 && header.int == 2 && header.int == bytes.size
    }
}
