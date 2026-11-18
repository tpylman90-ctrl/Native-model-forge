package com.modelforge.nativeeditor

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.util.AtomicFile
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * First real Android vertical slice: the user can create an exact-dimension
 * native C++ mesh, inspect it in Filament, reopen GLBs, and export GLB.
 *
 * Not yet an editor for arbitrary imported topology; never expose buttons
 * that suggest unimplemented mesh operations are working.
 */
class MainActivity : Activity(), Choreographer.FrameCallback {
    private val bg = Color.rgb(12, 20, 31)
    private val panel = Color.rgb(23, 37, 52)
    private val text = Color.rgb(232, 245, 251)
    private val muted = Color.rgb(168, 188, 204)
    private val accent = Color.rgb(54, 212, 188)
    private lateinit var surface: SurfaceView
    private lateinit var viewer: ModelViewer
    private lateinit var drawer: LinearLayout
    private lateinit var drawerToggle: Button
    private lateinit var status: TextView
    private lateinit var widthField: EditText
    private lateinit var heightField: EditText
    private lateinit var lengthField: EditText
    private lateinit var orbitPad: OrbitPad
    private lateinit var shapeOverlay: DirectShapeOverlay
    private lateinit var faceOverlay: FaceSelectionOverlay
    private lateinit var faceButton: Button
    private lateinit var extrudeDepthField: EditText
    private lateinit var faceSelectionText: TextView
    private lateinit var extrudeApplyButton: Button
    private var faceMode = false
    private var selectedFace: Int? = null
    private var currentGeneratedMesh: FloatArray? = null
    private var faceTouchStartX = 0f
    private var faceTouchStartY = 0f
    private var faceDragHandle = false
    private var faceDragBase = 0f
    private var faceDragPreview = 0f
    private lateinit var directShapeButton: Button
    private var directShapeMode = false
    private lateinit var cameraManipulator: Manipulator
    private var currentGlb: ByteArray? = null
    private var currentName = "Starter Box"
    private var importedPreview = false
    // The current editable primitive is independent of Filament render entities.
    private data class Shape(
        val width: Float, val height: Float, val length: Float,
        val topWidth: Float = 1f, val topLength: Float = 1f, val shiftX: Float = 0f,
        val extrudedFace: Int = -1, val extrusionDepth: Float = 0f
    )
    private var currentShape: Shape? = null
    // Native C++ Document owns object IDs, revisions, and in-session undo/redo.
    private val projectFile by lazy { AtomicFile(File(filesDir, "working_shape.mforge")) }
    private lateinit var topWidthSlider: SeekBar
    private lateinit var topLengthSlider: SeekBar
    private lateinit var shiftSlider: SeekBar
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button
    private var running = false
    private var startupFailed = false
    private var firstFrameCompleted = false
    private val startupPrefs by lazy {
        getSharedPreferences("model_forge_startup", Context.MODE_PRIVATE)
    }

    private fun markStartup(stage: String) {
        // Commit synchronously so a native crash still leaves a useful marker.
        startupPrefs.edit().putString("last_stage", stage)
            .putBoolean("incomplete", true).commit()
        Log.i("ModelForgeStartup", stage)
    }

    private fun completeStartup() {
        firstFrameCompleted = true
        startupPrefs.edit().putString("last_stage", "first_frame")
            .putBoolean("incomplete", false).apply()
    }
    private var lastFrameNs = 0L
    private var joystickFractionX = 0f
    private var joystickFractionY = 0f
    private var backgroundFill: IndirectLight? = null
    private val studioLights = mutableListOf<Int>()
    private val openCode = 401
    private val exportCode = 402

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        val previouslyInterrupted = startupPrefs.getBoolean("incomplete", false)
        val previousStage = startupPrefs.getString("last_stage", "unknown") ?: "unknown"
        createInterface()
        if (previouslyInterrupted) {
            // Do not repeat a fatal native renderer crash on every launch.
            startupFailed = true
            startupPrefs.edit().putBoolean("incomplete", false).apply()
            showStartupRecovery("Previous startup ended at: " + previousStage)
            return
        }
        try {
            markStartup("loading_filament_jni")
            // ModelViewer needs all THREE native layers. Gltfio.init() does
            // not initialize libfilament-utils-jni: missing Utils.init() can
            // crash on Manipulator/FramePacer native methods during startup.
            Utils.init()
            Gltfio.init()
            markStartup("constructing_model_viewer")
            // Filament's ModelViewer.render() REPLACES camera.lookAt using its
            // Manipulator every frame. The old UI wrote to viewer.camera
            // directly, so both joystick and swipe changes were overwritten.
            // Share this manipulator with the viewer and all input controls.
            cameraManipulator = Manipulator.Builder()
                .targetPosition(0f, 0f, 0f)
                .upVector(0f, 1f, 0f)
                .orbitHomePosition(1.55f, 0.82f, 2.76f)
                .orbitSpeed(0.009f, 0.009f)
                .viewport(surface.width.coerceAtLeast(1), surface.height.coerceAtLeast(1))
                .build(Manipulator.Mode.ORBIT)
            viewer = ModelViewer(
                surface, Engine.create(),
                UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK), cameraManipulator
            )
            markStartup("setting_up_lighting")
            setupLighting()
            viewer.camera.setExposure(13.0f, 1.0f / 125.0f, 100.0f)
            // Use Filament's tested one-finger orbit, two-finger pan and
            // pinch-to-zoom recognizer. All three update cameraManipulator.
            surface.setOnTouchListener { _, event ->
                if (::viewer.isInitialized && !startupFailed) {
                    if (!faceMode || !handleFaceGesture(event)) viewer.onTouchEvent(event)
                }
                true
            }
            markStartup("loading_starter_glb")
            if (!restoreWorkingShape()) createBox()
            markStartup("awaiting_first_frame")
        } catch (error: Throwable) {
            Log.e("ModelForgeStartup", "Startup failure", error)
            startupFailed = true
            val failedAt = startupPrefs.getString("last_stage", "unknown") ?: "unknown"
            startupPrefs.edit().putBoolean("incomplete", false).apply()
            showStartupRecovery("Initialization failed at " + failedAt +
                ": " + error.javaClass.simpleName + ": " + (error.message ?: "no message"))
        }
    }

    private fun showStartupRecovery(message: String) {
        drawer.visibility = View.GONE
        drawerToggle.text = "3D RENDERER RECOVERY MODE"
        drawerToggle.isEnabled = false
        status.text = "Model Forge launched in safe mode — renderer unavailable"
        val frame = status.parent as FrameLayout
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(23, 37, 52))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        overlay.addView(textLabel("3D VIEWPORT DID NOT START", 18f, accent))
        overlay.addView(textLabel(message, 14f, text))
        overlay.addView(textLabel(
            "You can copy this diagnostic for troubleshooting. No model files are removed.",
            13f, muted))
        overlay.addView(button("COPY DIAGNOSTIC") {
            val report = "Model Forge native v0.2.1\n" +
                "Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL +
                "\nAndroid API: " + android.os.Build.VERSION.SDK_INT +
                "\n" + message
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Model Forge startup report", report))
            toast("Diagnostic copied. Paste it into our chat.")
        })
        overlay.addView(button("RETRY 3D VIEW") {
            startupPrefs.edit().putBoolean("incomplete", false).apply()
            recreate()
        })
        frame.addView(overlay, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
    }

    private fun createInterface() {
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        setContentView(main)
        main.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            } else {
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }

        val title = TextView(this).apply {
            text = "MODEL FORGE    /    NATIVE"
            setTextColor(accent)
            textSize = 19f
            setPadding(dp(15), dp(12), dp(8), dp(5))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        main.addView(title)

        val topScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topScroll.addView(top)
        main.addView(topScroll)
        top.addView(button("＋ NEW BOX") { createBox() })
        top.addView(button("OPEN GLB") { requestOpen() })
        top.addView(button("SAVE GLB") { requestExport() })
        top.addView(button("?" ) { showHelp() })

        val canvas = FrameLayout(this).apply { setBackgroundColor(Color.rgb(19, 29, 43)) }
        surface = SurfaceView(this)
        canvas.addView(surface, FrameLayout.LayoutParams(-1, -1))
        main.addView(canvas, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            setTextColor(this@MainActivity.text)
            setBackgroundColor(Color.argb(165, 11, 21, 33))
            setPadding(dp(12), dp(9), dp(12), dp(9))
            textSize = 13f
            text = "Create • shape • share — foundation preview"
        }
        canvas.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        faceOverlay = FaceSelectionOverlay()
        canvas.addView(faceOverlay, FrameLayout.LayoutParams(-1, -1))
        faceOverlay.visibility = View.GONE

        // The overlay intercepts only touches on editing handles.
        // Every other gesture reaches the unchanged Filament manipulator.
        shapeOverlay = DirectShapeOverlay()
        canvas.addView(shapeOverlay, FrameLayout.LayoutParams(-1, -1))
        shapeOverlay.visibility = View.GONE

        orbitPad = OrbitPad().apply { contentDescription = "Camera orbit joystick" }
        val padParams = FrameLayout.LayoutParams(dp(154), dp(170), Gravity.END or Gravity.BOTTOM)
        padParams.rightMargin = dp(6)
        padParams.bottomMargin = dp(8)
        canvas.addView(orbitPad, padParams)

        drawerToggle = button("▼ HIDE TOOLS") { setDrawerVisible(drawer.visibility != View.VISIBLE) }
        drawerToggle.textSize = 16f
        drawerToggle.minimumHeight = dp(52)
        main.addView(drawerToggle, LinearLayout.LayoutParams(-1, dp(52)))

        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(12), dp(5), dp(12), dp(8))
        }
        main.addView(drawer)
        drawer.addView(textLabel("START SIMPLE   •   REAL 3D GEOMETRY", 14f, accent))
        drawer.addView(textLabel("Size your box, then create it. Advanced tools will use the same mesh.", 12f, muted))

        val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        drawer.addView(fields)
        widthField = dimensionField(fields, "WIDTH", "2.0")
        heightField = dimensionField(fields, "HEIGHT", "1.0")
        lengthField = dimensionField(fields, "LENGTH", "3.0")

        drawer.addView(textLabel("SHAPE • TOP SURFACE", 14f, accent))
        drawer.addView(textLabel("Move the top of your solid, then tap APPLY SHAPE.", 12f, muted))
        topWidthSlider = profileSlider(drawer, "TOP WIDTH", 35, 175, 100) { it.toString() + "%" }
        topLengthSlider = profileSlider(drawer, "TOP LENGTH", 35, 175, 100) { it.toString() + "%" }
        shiftSlider = profileSlider(drawer, "LEAN LEFT / RIGHT", -40, 40, 0) { it.toString() + "% of width" }
        val actions = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val actionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionsRow)
        drawer.addView(actions)
        actionsRow.addView(button("CREATE BOX") { createBox() })
        actionsRow.addView(button("APPLY SHAPE") { applyShape() })
        directShapeButton = button("◎ EDIT ON MODEL") { toggleDirectShape() }
        actionsRow.addView(directShapeButton)
        faceButton = button("▣ SELECT FACE") { toggleFaceMode() }
        actionsRow.addView(faceButton)
        undoButton = button("↶ UNDO") { undoShape() }
        redoButton = button("↷ REDO") { redoShape() }
        actionsRow.addView(undoButton)
        actionsRow.addView(redoButton)
        actionsRow.addView(button("FIT VIEW") {
            if (::cameraManipulator.isInitialized)
                cameraManipulator.jumpToBookmark(cameraManipulator.homeBookmark)
        })
        actionsRow.addView(button("⟳ TURN VIEW") { rotateCameraForCheck() })
        actionsRow.addView(button("ADVANCED • SOON") {
            toast("Top-surface shaping works now. Arbitrary face/edge editing is coming later.")
        })
        drawer.addView(textLabel("FACE TOOLS • TAP A SURFACE, THEN EXTRUDE", 13f, accent))
        faceSelectionText = textLabel("Face selection is off", 12f, muted)
        drawer.addView(faceSelectionText)
        val extrusionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        drawer.addView(extrusionRow)
        extrudeDepthField = EditText(this).apply {
            setSingleLine(true)
            setText("0.25")
            textSize = 18f
            setTextColor(this@MainActivity.text)
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setBackgroundColor(Color.rgb(35,53,68))
            setPadding(dp(12),dp(9),dp(10),dp(9))
            contentDescription = "Extrusion depth in metres, negative for recess"
        }
        extrusionRow.addView(extrudeDepthField, LinearLayout.LayoutParams(0,dp(52),1f))
        extrudeApplyButton = button("EXTRUDE m") { applyFaceInput() }
        extrusionRow.addView(extrudeApplyButton)
        val stepRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        drawer.addView(stepRow)
        stepRow.addView(button("− 0.10 m") { nudgeFace(-0.10f) })
        stepRow.addView(button("+ 0.10 m") { nudgeFace(0.10f) })
        drawer.addView(textLabel("One inset face feature per generated box. Negative = recess.", 11f, muted))
        drawer.addView(textLabel("Offline-first • GLB export • Community sharing planned, not active", 11f, muted))
        syncShapeUi()
    }

    private fun dimensionField(parent: LinearLayout, name: String, initial: String): EditText {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(5), dp(3), dp(6))
        }
        parent.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        column.addView(textLabel(name, 12f, muted))
        val field = EditText(this).apply {
            setSingleLine(true)
            setText(initial)
            textSize = 18f
            setTextColor(this@MainActivity.text)
            setSelectAllOnFocus(true)
            setBackgroundColor(Color.rgb(35, 53, 68))
            setPadding(dp(12), dp(9), dp(10), dp(9))
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        column.addView(field, LinearLayout.LayoutParams(-1, dp(52)))
        return field
    }

    private fun profileSlider(
        parent: LinearLayout, name: String, min: Int, max: Int, initial: Int,
        display: (Int) -> String
    ): SeekBar {
        val caption = textLabel(name + "  •  " + display(initial), 12f, muted)
        parent.addView(caption)
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar?, progress: Int, fromUser: Boolean) {
                caption.text = name + "  •  " + display(min + progress)
            }
            override fun onStartTrackingTouch(view: SeekBar?) = Unit
            override fun onStopTrackingTouch(view: SeekBar?) = Unit
        })
        parent.addView(bar, LinearLayout.LayoutParams(-1, dp(42)))
        return bar
    }

    private fun textLabel(value: String, fontSize: Float, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = fontSize
            setTextColor(color)
            setPadding(dp(2), dp(5), dp(2), dp(5))
        }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(this@MainActivity.text)
            minimumHeight = dp(54)
            setOnClickListener { action() }
        }

    private fun toggleDirectShape() {
        if (currentShape == null) {
            toast("On-model editing works with a generated box, not imported GLB previews.")
            return
        }
        directShapeMode = !directShapeMode
        if (directShapeMode) { faceMode = false; selectedFace = null }
        syncShapeUi()
        status.text = if (directShapeMode)
            "EDIT TOP: drag LEAN / WIDTH / LENGTH handles • lift finger to apply"
        else "View mode: drag to orbit • right joystick • pinch to zoom"
    }

    private fun toggleFaceMode() {
        if (currentShape == null) {
            toast("Face selection works only on editable generated boxes.")
            return
        }
        faceMode = !faceMode
        if (faceMode) directShapeMode = false
        selectedFace = null
        syncShapeUi()
        status.text = if (faceMode) "FACE MODE: tap a face, then drag the white circle or enter metres"
                      else "Face mode off • orbit normally"
    }

    private fun setDrawerVisible(opened: Boolean) {
        drawer.visibility = if (opened) View.VISIBLE else View.GONE
        drawerToggle.text = if (opened) "▼ HIDE TOOLS" else "▲ OPEN TOOLS"
        status.text = if (faceMode)
            "FACE MODE: tap surface • drag selected circle • or enter metres"
        else if (directShapeMode)
            "EDIT TOP: drag colored handles • other gestures still orbit"
        else if (opened) "Right joystick: orbit • pinch: zoom • drag: rotate"
        else "Full view • ▲ OPEN TOOLS to continue modeling"
    }

    private fun createBox() {
        try {
            val solid = Shape(
                widthField.text.toString().toFloat(),
                heightField.text.toString().toFloat(),
                lengthField.text.toString().toFloat()
            )
            if (renderShape(solid)) {
                NativeProject.resetAndCreate(solid.width, solid.height, solid.length,
                    solid.topWidth, solid.topLength, solid.shiftX)
                saveWorkingShape()
                syncShapeUi()
            }
        } catch (error: Exception) {
            toast("Enter valid positive dimensions (0.01–10000): " + (error.message ?: "invalid"))
        } catch (error: UnsatisfiedLinkError) {
            toast("Native modeling library missing; check APK install.")
        }
    }

    private fun renderShape(shape: Shape): Boolean {
        val mesh = NativeGeometry.generateFaceExtrudedBox(
            shape.width, shape.height, shape.length,
            shape.topWidth, shape.topLength, shape.shiftX,
            shape.extrudedFace, shape.extrusionDepth
        )
        val bytes = GlbBuilder.fromPacked(mesh)
        val ok = present(bytes, "Solid " + shape.width + " × " + shape.height + " × " + shape.length,
            false, shape)
        if (ok) {
            currentGeneratedMesh = mesh
            if (::faceOverlay.isInitialized) faceOverlay.invalidate()
        }
        return ok
    }

    private fun applyShape() {
        val previous = currentShape ?: run { toast("Create a box before shaping."); return }
        try {
            val next = previous.copy(
                width = widthField.text.toString().toFloat(),
                height = heightField.text.toString().toFloat(),
                length = lengthField.text.toString().toFloat(),
                topWidth = (topWidthSlider.progress + 35) / 100f,
                topLength = (topLengthSlider.progress + 35) / 100f,
                shiftX = (shiftSlider.progress - 40) / 100f
            )
            if (next == previous) { toast("Shape is already up to date."); return }
            commitDirectShape(next)
        } catch (error: Exception) {
            toast("Shape rejected: " + (error.message ?: "invalid dimensions"))
        } catch (error: UnsatisfiedLinkError) {
            toast("Native modeling library missing.")
        }
    }

    /** Direct viewport handles and slider controls commit the same native edit. */
    private fun commitDirectShape(next: Shape): Boolean {
        if (currentShape == null) return false
        if (next == currentShape) return true
        return try {
            if (!renderShape(next)) return false
            NativeProject.reshape(next.width, next.height, next.length,
                next.topWidth, next.topLength, next.shiftX)
            saveWorkingShape()
            syncShapeUi()
            if (directShapeMode) status.text =
                "TOP EDIT APPLIED • Undo available • drag another handle"
            true
        } catch (error: Exception) {
            toast("Edit rejected: " + (error.message ?: "invalid shape"))
            false
        }
    }

    private fun fromNative(): Shape? {
        val values = NativeProject.currentShape() ?: return null
        if (values.size != 8) return null
        return Shape(values[0], values[1], values[2],
            values[3], values[4], values[5], values[6].toInt(), values[7])
    }

    private fun undoShape() {
        if (currentShape == null || !NativeProject.undo()) return
        val previous = fromNative()
        if (previous == null || !renderShape(previous)) {
            NativeProject.redo()
            toast("Could not preview the previous shape.")
        } else {
            saveWorkingShape()
        }
        syncShapeUi()
    }

    private fun redoShape() {
        if (currentShape == null || !NativeProject.redo()) return
        val next = fromNative()
        if (next == null || !renderShape(next)) {
            NativeProject.undo()
            toast("Could not preview the next shape.")
        } else {
            saveWorkingShape()
        }
        syncShapeUi()
    }

    private fun saveWorkingShape() {
        var stream: FileOutputStream? = null
        try {
            val data = NativeProject.snapshot().toByteArray(StandardCharsets.UTF_8)
            stream = projectFile.startWrite()
            stream.write(data)
            projectFile.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) projectFile.failWrite(stream)
            Log.w("ModelForgeProject", "Could not persist working shape", error)
            toast("Shape updated, but local recovery copy could not be saved.")
        }
    }

    private fun restoreWorkingShape(): Boolean {
        if (!projectFile.baseFile.exists()) return false
        return try {
            val raw = projectFile.openRead().use { input ->
                val data = input.readBytes()
                require(data.size <= 1024 * 1024) { "Project is too large" }
                String(data, StandardCharsets.UTF_8)
            }
            NativeProject.load(raw)
            val saved = fromNative() ?: throw IllegalArgumentException("No editable shape")
            renderShape(saved)
        } catch (error: Exception) {
            Log.w("ModelForgeProject", "Could not restore working shape", error)
            NativeProject.clear()
            false
        }
    }

    private fun syncShapeUi() {
        val shape = currentShape
        topWidthSlider.isEnabled = shape != null
        topLengthSlider.isEnabled = shape != null
        shiftSlider.isEnabled = shape != null
        if (shape == null) { directShapeMode = false; faceMode = false; selectedFace = null }
        if (::faceButton.isInitialized) {
            faceButton.isEnabled = shape != null
            faceButton.text = if (faceMode) "● FACE MODE ON" else "▣ SELECT FACE"
        }
        if (::faceOverlay.isInitialized) {
            faceOverlay.visibility = if (faceMode) View.VISIBLE else View.GONE
            faceOverlay.invalidate()
        }
        if (::faceSelectionText.isInitialized) {
            val face = selectedFace
            faceSelectionText.text = if (shape == null) "Face selection unavailable for imported GLB"
                else if (!faceMode) "Tap SELECT FACE to enable"
                else if (face == null) "Tap any visible face on the box"
                else "Selected: " + faceName(face) + " • positive = raised, negative = recessed"
        }
        if (::extrudeApplyButton.isInitialized) extrudeApplyButton.isEnabled = faceMode && selectedFace != null
        if (::directShapeButton.isInitialized) {
            directShapeButton.isEnabled = shape != null
            directShapeButton.text = if (directShapeMode) "● TOP EDIT ON" else "◎ EDIT ON MODEL"
        }
        if (::shapeOverlay.isInitialized) {
            shapeOverlay.visibility = if (directShapeMode) View.VISIBLE else View.GONE
            shapeOverlay.invalidate()
        }
        undoButton.isEnabled = shape != null && NativeProject.canUndo()
        redoButton.isEnabled = shape != null && NativeProject.canRedo()
        if (shape != null) {
            widthField.setText(shape.width.toString())
            heightField.setText(shape.height.toString())
            lengthField.setText(shape.length.toString())
            topWidthSlider.progress = (shape.topWidth * 100f).toInt() - 35
            topLengthSlider.progress = (shape.topLength * 100f).toInt() - 35
            shiftSlider.progress = (shape.shiftX * 100f).toInt() + 40
        }
    }

    private fun present(
        bytes: ByteArray, name: String, imported: Boolean, shape: Shape? = null
    ): Boolean {
        if (!GlbBuilder.isValidHeader(bytes)) {
            toast("Invalid GLB format; model was not replaced.")
            return false
        }
        try {
            val keepCamera = shape != null && currentShape != null
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put(bytes)
            buffer.flip()
            viewer.loadModelGlb(buffer)
            viewer.transformToUnitCube(Float3(0f, 0f, 0f))
            currentGlb = bytes
            if (imported) currentGeneratedMesh = null
            currentName = name
            importedPreview = imported
            currentShape = shape
            syncShapeUi()
            if (!keepCamera && ::cameraManipulator.isInitialized) {
                cameraManipulator.jumpToBookmark(cameraManipulator.homeBookmark)
            }
            status.text = if (imported) "GLB preview: " + name + " • Import editing coming later"
                          else if (faceMode) "FACE MODE: tap a surface to select"
                          else if (directShapeMode) "TOP EDIT: drag handles • lift to apply"
                          else name + " • Native shaped solid • PBR viewport"
            return true
        } catch (error: Exception) {
            toast("GLB could not render: " + (error.message ?: "unsupported asset"))
            return false
        }
    }

    private fun setupLighting() {
        val engine = viewer.engine
        val irradiance = FloatArray(27)
        irradiance[0] = 0.63f; irradiance[1] = 0.67f; irradiance[2] = 0.74f
        backgroundFill = IndirectLight.Builder()
            .irradiance(3, irradiance).intensity(32000f).build(engine)
        viewer.scene.indirectLight = backgroundFill
        addLight(-0.35f, -0.9f, -0.55f, 40000f, 1.0f, 0.97f, 0.92f)
        addLight(0.86f, -0.25f, 0.45f, 18000f, 0.78f, 0.88f, 1.0f)
        addLight(-0.55f, -0.3f, 0.84f, 12000f, 1.0f, 0.85f, 0.74f)
    }

    private fun addLight(x: Float, y: Float, z: Float, intensity: Float, r: Float, g: Float, b: Float) {
        val entity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .direction(x, y, z).intensity(intensity).color(r, g, b)
            .castShadows(false).build(viewer.engine, entity)
        viewer.scene.addEntity(entity)
        studioLights.add(entity)
    }

    // Deliberate button-triggered camera check: tests the same native path
    // as the touch joystick without requiring a long press.
    private fun rotateCameraForCheck() {
        if (!::cameraManipulator.isInitialized || surface.width <= 0 || surface.height <= 0) {
            toast("The 3D viewport is not ready.")
            return
        }
        applyOrbitPixels(135, 0)
        status.text = "TURN VIEW: orbit command sent • drag model or use right joystick"
    }

    private fun applyOrbitPixels(deltaX: Int, deltaY: Int) {
        if (!::cameraManipulator.isInitialized || surface.width <= 0 || surface.height <= 0) return
        if (deltaX == 0 && deltaY == 0) return
        val centerX = surface.width / 2
        val centerY = surface.height / 2
        cameraManipulator.grabBegin(centerX, centerY, false)
        // Manipulator coordinates use a bottom-left origin; joystick Y is down-positive.
        cameraManipulator.grabUpdate(centerX + deltaX, centerY - deltaY)
        cameraManipulator.grabEnd()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running || startupFailed || !::viewer.isInitialized) return
        val dt = if (lastFrameNs > 0L)
            ((frameTimeNanos - lastFrameNs).toDouble() * 1e-9).coerceIn(0.0, 0.05).toFloat()
        else 1f / 60f
        lastFrameNs = frameTimeNanos
        try {
            if (::cameraManipulator.isInitialized) {
                if (orbitPad.dx != 0f || orbitPad.dy != 0f) {
                    // Integrate sub-pixel movement so gentle stick pressure
                    // still rotates smoothly on low-resolution phones.
                    joystickFractionX += orbitPad.dx * dt * 155f
                    joystickFractionY += orbitPad.dy * dt * 155f
                    val pixelsX = joystickFractionX.toInt()
                    val pixelsY = joystickFractionY.toInt()
                    if (pixelsX != 0 || pixelsY != 0) {
                        applyOrbitPixels(pixelsX, pixelsY)
                        joystickFractionX -= pixelsX.toFloat()
                        joystickFractionY -= pixelsY.toFloat()
                    }
                } else {
                    joystickFractionX = 0f
                    joystickFractionY = 0f
                }
                // Filament's native camera manipulator owns the final camera.
                // ModelViewer.render() reads its getLookAt() each frame.
                cameraManipulator.update(dt)
            }
            viewer.render(frameTimeNanos)
            if (directShapeMode) shapeOverlay.invalidate()
            if (faceMode) faceOverlay.invalidate()
            if (!firstFrameCompleted && surface.width > 0 && surface.height > 0) {
                completeStartup()
            }
        } catch (error: Throwable) {
            Log.e("ModelForgeStartup", "Frame failure", error)
            startupFailed = true
            val stage = startupPrefs.getString("last_stage", "render") ?: "render"
            startupPrefs.edit().putBoolean("incomplete", false).apply()
            showStartupRecovery("Frame error at " + stage +
                ": " + error.javaClass.simpleName + ": " + (error.message ?: "no message"))
            return
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun requestOpen() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        startActivityForResult(intent, openCode)
    }

    private fun requestExport() {
        if (currentGlb == null) { toast("Create or open a GLB first."); return }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream")
        intent.putExtra(Intent.EXTRA_TITLE, "ModelForgeNative-" + (if (importedPreview) "preview" else "box") + ".glb")
        startActivityForResult(intent, exportCode)
    }

    @Deprecated("Uses framework SAF callbacks to stay free of third-party UI services")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == openCode) {
                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    // Limit imported models to protect memory on budget Android phones.
                    val stream = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        require(stream.size() + n <= 80 * 1024 * 1024) { "GLB exceeds 80 MB limit" }
                        stream.write(buffer, 0, n)
                    }
                    stream.toByteArray()
                } ?: throw IllegalArgumentException("Document could not be read")
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported model"
                if (present(bytes, name, true)) {
                    // Import is still preview-only. Keep the last editable draft on disk.
                    NativeProject.clear()
                    syncShapeUi()
                }
            } else if (requestCode == exportCode) {
                val bytes = currentGlb ?: return
                contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: throw IllegalArgumentException("Cannot open destination")
                toast("Saved a real GLB to Android Files.")
            }
        } catch (error: Exception) {
            toast("File operation failed: " + (error.message ?: "unknown error"))
        }
    }

    private fun showHelp() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Model Forge Native — Foundation")
            .setMessage(
                "CREATE: Enter width, height and length, then tap CREATE BOX.\n\n" +
                "SHAPE: Adjust top width, top length and sideways lean, then tap APPLY SHAPE. Or tap EDIT ON MODEL, hide tools and drag the three TOP handles. Undo/Redo uses the native document during this session. Your last editable shape is restored after relaunch.\n\n" +
                "VIEW: Drag the model or use the right joystick to orbit. Pinch to zoom; two fingers pan. FIT VIEW resets the camera; TURN VIEW tests orbit. ▼ HIDE TOOLS expands the screen.\n\n" +
                "FACE EDIT: SELECT FACE, tap a visible face to highlight it, then drag its centre circle or enter a signed depth in metres and press EXTRUDE. Negative values make a recess. One extrusion per generated box; selecting another face replaces it.\n\n" +
                "SAVE GLB: Keep the real mesh on your phone. OPEN GLB: Preview existing models.\n\n" +
                "NEXT: Multi-face editing, edge beveling and arbitrary imported mesh topology. Imported GLBs remain preview-only."
            ).setPositiveButton("GOT IT", null).show()
    }

    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onResume() {
        super.onResume()
        running = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }
    override fun onPause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        if (::orbitPad.isInitialized) { orbitPad.dx = 0f; orbitPad.dy = 0f }
        if (::shapeOverlay.isInitialized) shapeOverlay.cancelGesture()
        faceDragHandle = false
        if (::cameraManipulator.isInitialized) cameraManipulator.grabEnd()
        joystickFractionX = 0f; joystickFractionY = 0f
        super.onPause()
    }
    override fun onDestroy() {
        running = false
        if (::viewer.isInitialized) {
            try { viewer.destroyModel() } catch (_: Exception) { }
        }
        super.onDestroy()
    }


    // First on-model direct manipulation: a generated box's top surface only.
    // These are screen-projected geometry handles, not arbitrary GLB picking.
    private enum class TopHandleKind { LEAN, WIDTH, LENGTH }
    private data class TopHandle(
        val kind: TopHandleKind, val label: String, val center: PointF,
        val basis: PointF, val color: Int
    )

    private inner class DirectShapeOverlay : View(this@MainActivity) {
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
        private var selected: TopHandle? = null
        private var initial: Shape? = null
        private var proposed: Shape? = null
        private var touchStartX = 0f
        private var touchStartY = 0f
        private var fingerX = 0f
        private var fingerY = 0f

        fun cancelGesture() {
            selected = null
            initial = null
            proposed = null
            invalidate()
        }

        // Filament Java camera matrices are column-major. The GLB is centered
        // and uniformly rescaled by ModelViewer.transformToUnitCube(); compute
        // that same transform from the generated vertex bounds for handle points.
        private fun projectPoint(
            viewMatrix: DoubleArray, projection: DoubleArray,
            x: Double, y: Double, z: Double
        ): PointF? {
            fun transform(m: DoubleArray, v: DoubleArray): DoubleArray =
                DoubleArray(4) { i ->
                    m[i] * v[0] + m[i + 4] * v[1] +
                    m[i + 8] * v[2] + m[i + 12] * v[3]
                }
            val clip = transform(projection,
                transform(viewMatrix, doubleArrayOf(x, y, z, 1.0)))
            if (!clip[3].isFinite() || clip[3] <= 1e-6) return null
            val nx = clip[0] / clip[3]
            val ny = clip[1] / clip[3]
            if (!nx.isFinite() || !ny.isFinite() ||
                kotlin.math.abs(nx) > 1.08 || kotlin.math.abs(ny) > 1.08) return null
            return PointF(((nx + 1.0) * width / 2.0).toFloat(),
                          ((1.0 - ny) * height / 2.0).toFloat())
        }

        private fun handles(): List<TopHandle> {
            val shape = currentShape ?: return emptyList()
            if (!directShapeMode || !::viewer.isInitialized || width < 2 || height < 2)
                return emptyList()
            val w = shape.width.toDouble()
            val h = shape.height.toDouble()
            val l = shape.length.toDouble()
            val shift = shape.shiftX.toDouble() * w
            val tw = shape.topWidth.toDouble()
            val tl = shape.topLength.toDouble()
            val minX = minOf(-w / 2.0, shift - w * tw / 2.0)
            val maxX = maxOf(w / 2.0, shift + w * tw / 2.0)
            val minZ = minOf(-l / 2.0, -l * tl / 2.0)
            val maxZ = maxOf(l / 2.0, l * tl / 2.0)
            val scale = 2.0 / maxOf(maxX - minX, h, maxZ - minZ)
            val centerX = (minX + maxX) / 2.0
            val centerZ = (minZ + maxZ) / 2.0
            val topY = (h / 2.0) * scale
            // Hide the top-face handles if the camera is below its plane.
            val camera = viewer.camera
            if (camera.getPosition(null)[1] <= topY + 0.015) return emptyList()
            val viewMatrix = camera.getViewMatrix(DoubleArray(16))
            val projection = camera.getProjectionMatrix(null)
            fun at(x: Double, z: Double): PointF? =
                projectPoint(viewMatrix, projection,
                    (x - centerX) * scale, topY, (z - centerZ) * scale)
            val lean = at(shift, 0.0) ?: return emptyList()
            val topRight = at(shift + w * tw / 2.0, 0.0)
            val topFront = at(shift, l * tl / 2.0)
            val leanStep = at(shift + w * 0.10, 0.0)
            val widthStep = at(shift + w * (tw + 0.10) / 2.0, 0.0)
            val lengthStep = at(shift, l * (tl + 0.10) / 2.0)
            fun axis(origin: PointF, end: PointF): PointF =
                PointF((end.x - origin.x) * 10f, (end.y - origin.y) * 10f)
            val output = mutableListOf<TopHandle>()
            if (leanStep != null)
                output.add(TopHandle(TopHandleKind.LEAN, "LEAN", lean,
                    axis(lean, leanStep), Color.rgb(255, 210, 96)))
            if (topRight != null && widthStep != null)
                output.add(TopHandle(TopHandleKind.WIDTH, "WIDTH", topRight,
                    axis(topRight, widthStep), Color.rgb(54, 212, 188)))
            if (topFront != null && lengthStep != null)
                output.add(TopHandle(TopHandleKind.LENGTH, "LENGTH", topFront,
                    axis(topFront, lengthStep), Color.rgb(188, 149, 255)))
            return output.filter {
                val squared = it.basis.x * it.basis.x + it.basis.y * it.basis.y
                squared > 12f * 12f
            }
        }

        override fun onDraw(canvas: Canvas) {
            if (!directShapeMode) return
            val shown = handles()
            ink.style = Paint.Style.STROKE
            ink.strokeWidth = dp(2).toFloat()
            shown.forEach { handle ->
                ink.color = handle.color
                canvas.drawCircle(handle.center.x, handle.center.y,
                    dp(22).toFloat(), ink)
                ink.style = Paint.Style.FILL
                canvas.drawCircle(handle.center.x, handle.center.y,
                    dp(10).toFloat(), ink)
                ink.color = Color.WHITE
                ink.textAlign = Paint.Align.CENTER
                ink.textSize = dp(12).toFloat()
                ink.setShadowLayer(dp(3).toFloat(), 0f, 0f, Color.BLACK)
                canvas.drawText(handle.label, handle.center.x,
                    handle.center.y - dp(30), ink)
                ink.clearShadowLayer()
                ink.style = Paint.Style.STROKE
            }
            val active = selected
            if (active != null) {
                ink.color = Color.WHITE
                ink.strokeWidth = dp(2).toFloat()
                canvas.drawLine(active.center.x, active.center.y, fingerX, fingerY, ink)
                ink.style = Paint.Style.FILL
                canvas.drawCircle(fingerX, fingerY, dp(8).toFloat(), ink)
            }
            ink.style = Paint.Style.FILL
        }

        private fun candidate(base: Shape, handle: TopHandle, x: Float, y: Float): Shape {
            val dx = x - touchStartX
            val dy = y - touchStartY
            val vector = handle.basis
            val normSq = vector.x * vector.x + vector.y * vector.y
            if (normSq < 144f) return base
            val delta = (dx * vector.x + dy * vector.y) / normSq
            return when (handle.kind) {
                TopHandleKind.LEAN -> base.copy(
                    shiftX = (base.shiftX + delta).coerceIn(-0.4f, 0.4f))
                TopHandleKind.WIDTH -> base.copy(
                    topWidth = (base.topWidth + delta).coerceIn(0.35f, 1.75f))
                TopHandleKind.LENGTH -> base.copy(
                    topLength = (base.topLength + delta).coerceIn(0.35f, 1.75f))
            }
        }

        private fun updateTouch(x: Float, y: Float) {
            val handle = selected ?: return
            val base = initial ?: return
            fingerX = x
            fingerY = y
            val next = candidate(base, handle, x, y)
            proposed = next
            val amount = when (handle.kind) {
                TopHandleKind.LEAN -> (next.shiftX * 100).toInt().toString() + "%"
                TopHandleKind.WIDTH -> (next.topWidth * 100).toInt().toString() + "%"
                TopHandleKind.LENGTH -> (next.topLength * 100).toInt().toString() + "%"
            }
            status.text = handle.label + ": " + amount + " • lift finger to apply"
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!directShapeMode) return false
                    val target = handles().minByOrNull {
                        val dx = it.center.x - event.x
                        val dy = it.center.y - event.y
                        dx * dx + dy * dy
                    } ?: return false
                    val dx = target.center.x - event.x
                    val dy = target.center.y - event.y
                    if (dx * dx + dy * dy > dp(44).toFloat() * dp(44)) return false
                    selected = target
                    initial = currentShape
                    proposed = currentShape
                    touchStartX = event.x
                    touchStartY = event.y
                    fingerX = event.x
                    fingerY = event.y
                    parent.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (selected == null) return false
                    updateTouch(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (selected == null) return false
                    updateTouch(event.x, event.y)
                    val next = proposed
                    val original = initial
                    cancelGesture()
                    if (next != null && original != null && next != original)
                        commitDirectShape(next)
                    else status.text = "TOP EDIT: no change • drag a handle"
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (selected == null) return false
                    cancelGesture()
                    status.text = "TOP EDIT: drag a handle to edit"
                    return true
                }
                else -> return selected != null
            }
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private inner class OrbitPad : View(this@MainActivity) {
        var dx = 0f
        var dy = 0f
        private val draw = Paint(Paint.ANTI_ALIAS_FLAG)
        private var finger = -1
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = (minOf(width, height) * 0.34f)
            draw.color = Color.argb(128, 17, 35, 48)
            canvas.drawCircle(cx, cy, radius + dp(8), draw)
            draw.style = Paint.Style.STROKE
            draw.strokeWidth = dp(2).toFloat()
            draw.color = Color.argb(170, 80, 187, 201)
            canvas.drawCircle(cx, cy, radius, draw)
            draw.style = Paint.Style.FILL
            draw.color = Color.argb(230, 49, 219, 186)
            canvas.drawCircle(cx + dx * radius, cy + dy * radius, dp(22).toFloat(), draw)
            draw.color = Color.WHITE
            draw.textSize = dp(12).toFloat()
            draw.textAlign = Paint.Align.CENTER
            canvas.drawText("ORBIT", cx, cy - radius - dp(14), draw)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (finger < 0) {
                        finger = event.getPointerId(event.actionIndex)
                        update(event.getX(event.actionIndex), event.getY(event.actionIndex))
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = event.findPointerIndex(finger)
                    if (index >= 0) update(event.getX(index), event.getY(index))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL ||
                        event.getPointerId(event.actionIndex) == finger) {
                        finger = -1; dx = 0f; dy = 0f; invalidate()
                    }
                }
            }
            return true
        }
        private fun update(x: Float, y: Float) {
            val r = minOf(width, height) * 0.34f
            dx = ((x - width / 2f) / r).coerceIn(-1f, 1f)
            dy = ((y - height / 2f) / r).coerceIn(-1f, 1f)
            if (dx * dx + dy * dy < 0.02f) { dx = 0f; dy = 0f }
            invalidate()
        }
    }
}
