package com.modelforge.nativeeditor

/**
 * Canonical native editing state for the generated box.
 * Current profile and stable ID survive app restarts in the local draft file.
 * Undo/redo history is intentionally in-session only for this milestone.
 */
object NativeProject {
    init { System.loadLibrary("model_forge_core") }
    external fun resetAndCreate(width: Float, height: Float, length: Float,
                                topWidth: Float, topLength: Float, shiftX: Float)
    external fun reshape(width: Float, height: Float, length: Float,
                         topWidth: Float, topLength: Float, shiftX: Float)
    external fun setFaceExtrusion(faceIndex: Int, depth: Float)
    external fun undo(): Boolean
    external fun redo(): Boolean
    external fun canUndo(): Boolean
    external fun canRedo(): Boolean
    external fun currentShape(): FloatArray?
    external fun snapshot(): String
    external fun load(serialized: String)
    external fun clear()
}
