package com.modelforge.nativeeditor

object NativeGeometry {
    init { System.loadLibrary("model_forge_core") }
    // Returns 36 triangle vertices, each packed as [px,py,pz,nx,ny,nz].
    external fun generateBox(width: Float, height: Float, length: Float): FloatArray
    external fun generateFaceExtrudedBox(
        width: Float, height: Float, length: Float,
        topWidth: Float, topLength: Float, shiftX: Float,
        faceIndex: Int, depth: Float
    ): FloatArray
    external fun generateProfiledBox(
        width: Float, height: Float, length: Float,
        topWidth: Float, topLength: Float, shiftX: Float
    ): FloatArray
}
