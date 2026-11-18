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
import android.widget.ScrollView
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
    private lateinit var drawerScroll: ScrollView
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
    private lateinit var regionWidthSlider: SeekBar
    private lateinit var regionHeightSlider: SeekBar
    private lateinit var regionOffsetUSlider: SeekBar
    private lateinit var regionOffsetVSlider: SeekBar
    private lateinit var regionRectButton: Button
    private lateinit var regionOvalButton: Button
    private var regionKind=0
    private var baseGeneratedMesh: FloatArray? = null
    private var faceMode = false
    private lateinit var drawRegionButton: Button
    private lateinit var regionCurveButton: Button
    private var drawAreaMode = false
    private var drawStartUv: FaceUv? = null
    private var drawPreview: FaceArea? = null
    private var drawingFace = -1
    private var edgeDragIndex = -1
    private var edgeStartUv: FaceUv? = null
    private var edgeStartArea: FaceArea? = null
    private var edgeLiveArea: FaceArea? = null
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
    private data class FaceArea(
        val kind: Int = -1,
        val width: Float = 0.55f,
        val height: Float = 0.55f,
        val offsetU: Float = 0f,
        val offsetV: Float = 0f,
        val depth: Float = 0f,
        val curves: List<Float> = List(4) { 0f }
    )
    private data class FaceUv(val u: Float, val v: Float)
    private data class Shape(
        val width: Float, val height: Float, val length: Float,
        val topWidth: Float = 1f, val topLength: Float = 1f, val shiftX: Float = 0f,
        val areas: List<FaceArea> = List(6) { FaceArea() }
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
        drawerScroll.visibility = View.GONE
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
        drawerScroll=ScrollView(this).apply {
            isFillViewport=false
            isVerticalScrollBarEnabled=true
            addView(drawer)
        }
        main.addView(drawerScroll,LinearLayout.LayoutParams(-1,
            minOf(dp(460),(resources.displayMetrics.heightPixels*0.43f).toInt())))
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
        drawRegionButton=button("▱ DRAW AREA") { toggleDrawArea() }
        actionsRow.addView(drawRegionButton)
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
            toast("Face area selection works on generated boxes. Edge/vertex editing is coming later.")
        })
        drawer.addView(textLabel("FACE TOOLS • DRAW ON THE MODEL", 13f, accent))
        drawer.addView(button("✎ START DRAWING ON FACE") { if(!drawAreaMode) toggleDrawArea()
            else toast("Already drawing. Drag across a visible box face.") })
        drawer.addView(textLabel("DRAW AREA: touch one point and drag to size. Bend the four cyan mid-edge handles, then PULL.",12f,muted))
        faceSelectionText = textLabel("Face selection is off", 12f, muted)
        drawer.addView(faceSelectionText)
        val areaShapeRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        drawer.addView(areaShapeRow)
        regionRectButton=button("▭ RECTANGLE") { regionKind=0; updateAreaButtons(); faceOverlay.invalidate() }
        regionOvalButton=button("◯ OVAL") { regionKind=1; updateAreaButtons(); faceOverlay.invalidate() }
        regionCurveButton=button("〰 CURVE") { regionKind=2; updateAreaButtons(); faceOverlay.invalidate() }
        areaShapeRow.addView(regionRectButton,LinearLayout.LayoutParams(0,dp(52),1f))
        areaShapeRow.addView(regionOvalButton,LinearLayout.LayoutParams(0,dp(52),1f))
        areaShapeRow.addView(regionCurveButton,LinearLayout.LayoutParams(0,dp(52),1f))
        regionWidthSlider=profileSlider(drawer,"AREA WIDTH",15,85,55) { "$it% of face" }
        regionHeightSlider=profileSlider(drawer,"AREA HEIGHT",15,85,55) { "$it% of face" }
        regionOffsetUSlider=profileSlider(drawer,"LEFT / RIGHT POSITION",-80,80,0) { "$it%" }
        regionOffsetVSlider=profileSlider(drawer,"UP / DOWN POSITION",-80,80,0) { "$it%" }
        updateAreaButtons()
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
        drawer.addView(button("REMOVE SELECTED FACE EDIT") { removeFaceEdit() })
        drawer.addView(textLabel("Each face keeps its own shape, size, and depth. Other faces stay intact.", 11f, muted))
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
                if(fromUser && ::faceOverlay.isInitialized) faceOverlay.invalidate()
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
        if (directShapeMode) {
            faceMode = false; drawAreaMode = false; selectedFace = null
            drawPreview=null; edgeDragIndex=-1
        }
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
        else drawAreaMode=false
        drawPreview=null
        edgeDragIndex=-1
        selectedFace = null
        syncShapeUi()
        status.text = if (faceMode) "FACE MODE: tap a face, then drag the white circle or enter metres"
                      else "Face mode off • orbit normally"
    }

    private fun toggleDrawArea() {
        if(currentShape==null) {
            toast("Draw a region on a generated box, not an imported GLB.")
            return
        }
        faceMode=true
        directShapeMode=false
        drawAreaMode=!drawAreaMode
        drawStartUv=null
        drawPreview=null
        drawingFace=-1
        edgeDragIndex=-1
        selectedFace=null
        syncShapeUi()
        status.text=if(drawAreaMode) "DRAW AREA: touch a face and drag out your selection"
                    else "FACE MODE: tap a face or draw an area"
        if(drawAreaMode) setDrawerVisible(false)
    }

    private fun setDrawerVisible(opened: Boolean) {
        drawer.visibility = if (opened) View.VISIBLE else View.GONE
        drawerScroll.visibility = if (opened) View.VISIBLE else View.GONE
        drawerToggle.text = if (opened) "▼ HIDE TOOLS" else "▲ OPEN TOOLS"
        status.text = if(drawAreaMode)
            "DRAW AREA: drag from first corner to opposite corner"
        else if (faceMode)
            "FACE MODE: tap surface • bend curved edges • or pull"
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
        val parts=FloatArray(60)
        shape.areas.forEachIndexed { face,f ->
            val p=face*10
            parts[p]=f.kind.toFloat()
            parts[p+1]=f.width
            parts[p+2]=f.height
            parts[p+3]=f.offsetU
            parts[p+4]=f.offsetV
            parts[p+5]=f.depth
            for(edge in 0..3) parts[p+6+edge]=f.curves[edge]
        }
        val mesh=NativeGeometry.generateMultiFaceBox(
            shape.width,shape.height,shape.length,
            shape.topWidth,shape.topLength,shape.shiftX,parts
        )
        val bytes = GlbBuilder.fromPacked(mesh)
        val ok = present(bytes, "Solid " + shape.width + " × " + shape.height + " × " + shape.length,
            false, shape)
        if (ok) {
            currentGeneratedMesh = mesh
            baseGeneratedMesh=NativeGeometry.generateProfiledBox(
                shape.width,shape.height,shape.length,
                shape.topWidth,shape.topLength,shape.shiftX)
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
        val original = currentShape ?: return false
        return try {
            NativeProject.reshape(next.width, next.height, next.length,
                next.topWidth, next.topLength, next.shiftX)
            if (!renderShape(next)) {
                NativeProject.undo()
                renderShape(original)
                toast("Shape preview failed; previous edit restored.")
                return false
            }
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
        if (values.size != 66) return null
        val areas=List(6) { face ->
            val p=6+face*10
            FaceArea(values[p].toInt(),values[p+1],values[p+2],
                     values[p+3],values[p+4],values[p+5],
                     List(4) { edge->values[p+6+edge] })
        }
        return Shape(values[0], values[1], values[2],
            values[3], values[4], values[5], areas)
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
        selectedFace?.let { syncRegionUi(it) }
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
        selectedFace?.let { syncRegionUi(it) }
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
        if (shape == null) {
            directShapeMode=false;faceMode=false;drawAreaMode=false
            selectedFace=null;drawPreview=null;edgeDragIndex=-1
        }
        if (::drawRegionButton.isInitialized) {
            drawRegionButton.isEnabled=shape!=null
            drawRegionButton.text=if(drawAreaMode) "● DRAWING AREA" else "▱ DRAW AREA"
        }
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
                else "Selected: " + faceName(face) + " • " +
                    (shape.areas.count {it.kind>=0 && kotlin.math.abs(it.depth)>1e-6f}) +
                    " edited face(s) • positive raised / negative recessed"
        }
        if (::extrudeApplyButton.isInitialized) extrudeApplyButton.isEnabled = faceMode && selectedFace != null
        if (::regionRectButton.isInitialized) regionRectButton.isEnabled=faceMode && selectedFace!=null
        if (::regionOvalButton.isInitialized) regionOvalButton.isEnabled=faceMode && selectedFace!=null
        if (::regionCurveButton.isInitialized) regionCurveButton.isEnabled=faceMode && selectedFace!=null
        if (::regionWidthSlider.isInitialized) {
            for(slider in arrayOf(regionWidthSlider,regionHeightSlider,regionOffsetUSlider,regionOffsetVSlider))
                slider.isEnabled=faceMode && selectedFace!=null
        }
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
            if (imported) { currentGeneratedMesh = null; baseGeneratedMesh = null }
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
                "FACE EDIT: SELECT FACE, tap any visible original box face. Choose RECTANGLE or OVAL and adjust AREA WIDTH, AREA HEIGHT, and X/Y position sliders. The selected outline previews the area. Drag the PULL circle or enter signed metres and press EXTRUDE. Negative values make a recess. Every original face keeps its own edit; REMOVE SELECTED FACE EDIT clears only that face.\n\n" +
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

    // Geometric face picking uses the exact vertices used to build the GLB.
    // No fabricated UI coordinates, no imported GLB editing, and no GPU readback.
    private data class ScreenPoint(val x: Float, val y: Float, val distance: Double)

    private inner class MeshProjector(private val packed: FloatArray) {
        private val view = viewer.camera.getViewMatrix(DoubleArray(16))
        private val projection = viewer.camera.getProjectionMatrix(null)
        private val center: DoubleArray
        private val scale: Double

        init {
            val min = DoubleArray(3) { Double.POSITIVE_INFINITY }
            val max = DoubleArray(3) { Double.NEGATIVE_INFINITY }
            for(i in packed.indices step 6) {
                for(axis in 0..2) {
                    val v=packed[i+axis].toDouble()
                    min[axis] = kotlin.math.min(min[axis],v)
                    max[axis] = kotlin.math.max(max[axis],v)
                }
            }
            center = DoubleArray(3) { (min[it]+max[it])*0.5 }
            scale = 2.0 / maxOf(max[0]-min[0],max[1]-min[1],max[2]-min[2])
        }

        fun at(vertex: Int): ScreenPoint? {
            val i=vertex*6
            if(i<0 || i+2>=packed.size) return null
            return atWorld(packed[i],packed[i+1],packed[i+2])
        }

        fun atWorld(wx:Float,wy:Float,wz:Float):ScreenPoint? {
            val x=(wx.toDouble()-center[0])*scale
            val y=(wy.toDouble()-center[1])*scale
            val z=(wz.toDouble()-center[2])*scale
            fun mul(m: DoubleArray, a: Double, b: Double, c: Double, d: Double): DoubleArray =
                DoubleArray(4) { j ->
                    m[j]*a + m[j+4]*b + m[j+8]*c + m[j+12]*d
                }
            val eye=mul(view,x,y,z,1.0)
            if(!eye[2].isFinite() || eye[2]>=-0.01) return null
            val clip=mul(projection,eye[0],eye[1],eye[2],eye[3])
            if(clip[3] <= 1e-6 || !clip[3].isFinite()) return null
            val nx=clip[0]/clip[3]
            val ny=clip[1]/clip[3]
            if(!nx.isFinite() || !ny.isFinite() ||
                kotlin.math.abs(nx)>4.0 || kotlin.math.abs(ny)>4.0) return null
            return ScreenPoint(((nx+1.0)*surface.width*0.5).toFloat(),
                ((1.0-ny)*surface.height*0.5).toFloat(),-eye[2])
        }

        fun visibleFace(face: Int, shape: Shape): Boolean {
            val quad=faceQuadIndices(face,shape,packed.size/6) ?: return false
            val first=quad[0]*6
            val px=(packed[first].toDouble()-center[0])*scale
            val py=(packed[first+1].toDouble()-center[1])*scale
            val pz=(packed[first+2].toDouble()-center[2])*scale
            val camera=viewer.camera.getPosition(null)
            val dot=packed[first+3]*(camera[0]-px).toFloat() +
                packed[first+4]*(camera[1]-py).toFloat() +
                packed[first+5]*(camera[2]-pz).toFloat()
            return dot>0f
        }
    }

    private fun faceName(id: Int): String =
        arrayOf("FRONT", "REAR", "RIGHT", "LEFT", "TOP", "BOTTOM")
            .getOrElse(id) { "UNKNOWN" }

    private fun activeFaceVertices(f: FaceArea): Int =
        if(f.kind<0 || kotlin.math.abs(f.depth)<1e-6f) 6
        else if(f.kind==0) 54 else 390

    private fun faceStart(face:Int, shape:Shape):Int {
        var offset=0
        for(i in 0 until face) offset+=activeFaceVertices(shape.areas[i])
        return offset
    }

    private fun faceQuadIndices(face: Int, shape: Shape, vertexCount: Int): IntArray? {
        if(face !in 0..5) return null
        val start=faceStart(face,shape)
        val f=shape.areas[face]
        val count=activeFaceVertices(f)
        if(start<0 || start+count>vertexCount) return null
        return if(count==6) intArrayOf(start,start+1,start+2,start+5)
        else {
            val n=if(f.kind==0) 4 else 32
            val capStart=start+if(n==4) 48 else 300
            IntArray(n) { i -> when(i) {
                0 -> capStart
                1 -> capStart+1
                else -> capStart+(i-2)*3+2
            }}
        }
    }

    private fun faceForTriangle(index:Int,shape:Shape):Int {
        var triangleBase=0
        for(i in 0..5) {
            triangleBase+=activeFaceVertices(shape.areas[i])/3
            if(index<triangleBase) return i
        }
        return 5
    }

    private fun pickFace(x: Float, y: Float): Int? {
        val shape=currentShape ?: return null
        val packed=currentGeneratedMesh ?: return null
        if(!::viewer.isInitialized || !faceMode) return null
        val mapper=MeshProjector(packed)
        var nearest=Double.POSITIVE_INFINITY
        var result: Int? = null
        for(t in 0 until packed.size/18) {
            val face=faceForTriangle(t,shape)
            if(!mapper.visibleFace(face,shape)) continue
            val a=mapper.at(t*3) ?: continue
            val b=mapper.at(t*3+1) ?: continue
            val c=mapper.at(t*3+2) ?: continue
            val d=(b.y-c.y)*(a.x-c.x)+(c.x-b.x)*(a.y-c.y)
            if(kotlin.math.abs(d)<0.001f) continue
            val u=((b.y-c.y)*(x-c.x)+(c.x-b.x)*(y-c.y))/d
            val v=((c.y-a.y)*(x-c.x)+(a.x-c.x)*(y-c.y))/d
            val w=1f-u-v
            if(u < -0.001f || v < -0.001f || w < -0.001f) continue
            val distance=u*a.distance+v*b.distance+w*c.distance
            if(distance < nearest) {
                nearest=distance
                result=face
            }
        }
        return result
    }

    /** Face-local coordinates are derived from the projected original face
     * corners. This deliberately uses the same camera as the selection mesh,
     * so a finger drag maps to the face instead of the phone screen axes. */
    private fun screenFaceUv(face:Int,x:Float,y:Float):FaceUv? {
        val base=baseGeneratedMesh ?: return null
        val packed=currentGeneratedMesh ?: return null
        if(face !in 0..5 || !::viewer.isInitialized) return null
        val projector=MeshProjector(packed)
        val offset=face*36
        val corners=intArrayOf(0,1,2,5).map { corner ->
            val i=offset+corner*6
            projector.atWorld(base[i],base[i+1],base[i+2]) ?: return null
        }
        fun interpolate(a:Int,b:Int,c:Int):Triple<Float,Float,Float>? {
            val pa=corners[a];val pb=corners[b];val pc=corners[c]
            val d=(pb.y-pc.y)*(pa.x-pc.x)+(pc.x-pb.x)*(pa.y-pc.y)
            if(kotlin.math.abs(d)<1e-4f) return null
            val alpha=((pb.y-pc.y)*(x-pc.x)+(pc.x-pb.x)*(y-pc.y))/d
            val beta=((pc.y-pa.y)*(x-pc.x)+(pa.x-pc.x)*(y-pc.y))/d
            return Triple(alpha,beta,1f-alpha-beta)
        }
        val t0=interpolate(0,1,2) ?: return null
        val t1=interpolate(0,2,3) ?: return null
        val inside0=minOf(t0.first,t0.second,t0.third)
        val inside1=minOf(t1.first,t1.second,t1.third)
        return if(inside0>=inside1) {
            FaceUv((t0.second+t0.third).coerceIn(-0.5f,1.5f),
                   t0.third.coerceIn(-0.5f,1.5f))
        } else {
            FaceUv(t1.second.coerceIn(-0.5f,1.5f),
                  (t1.second+t1.third).coerceIn(-0.5f,1.5f))
        }
    }

    private fun drawnArea(start:FaceUv,end:FaceUv):FaceArea {
        val u0=start.u.coerceIn(0.06f,0.94f)
        val u1=end.u.coerceIn(0.06f,0.94f)
        val v0=start.v.coerceIn(0.06f,0.94f)
        val v1=end.v.coerceIn(0.06f,0.94f)
        val width=kotlin.math.abs(u1-u0).coerceIn(0.15f,0.85f)
        val height=kotlin.math.abs(v1-v0).coerceIn(0.15f,0.85f)
        val centerU=((u0+u1)*0.5f).coerceIn(0.5f-0.4f*(1f-width),0.5f+0.4f*(1f-width))
        val centerV=((v0+v1)*0.5f).coerceIn(0.5f-0.4f*(1f-height),0.5f+0.4f*(1f-height))
        val offsetU=((centerU-0.5f)*2f/(1f-width)).coerceIn(-0.8f,0.8f)
        val offsetV=((centerV-0.5f)*2f/(1f-height)).coerceIn(-0.8f,0.8f)
        return FaceArea(kind=2,width=width,height=height,offsetU=offsetU,
            offsetV=offsetV,depth=0f)
    }

    private fun bendArea(area:FaceArea,edge:Int,delta:FaceUv):FaceArea {
        if(edge !in 0..3) return area
        val shortSide=minOf(area.width,area.height)
        val inward=when(edge) {
            0 -> delta.v
            1 -> -delta.u
            2 -> -delta.v
            else -> delta.u
        }
        val centerU=0.5f+area.offsetU*(1f-area.width)*0.5f
        val centerV=0.5f+area.offsetV*(1f-area.height)*0.5f
        val margin=when(edge) {
            0 -> centerV-area.height*0.5f
            1 -> 1f-(centerU+area.width*0.5f)
            2 -> 1f-(centerV+area.height*0.5f)
            else -> centerU-area.width*0.5f
        }
        val minCurve=-minOf(0.2f,0.75f*margin/shortSide)
        val curves=area.curves.toMutableList()
        curves[edge]=(area.curves[edge]+inward/shortSide).coerceIn(minCurve,0.2f)
        return area.copy(kind=2,curves=curves)
    }

    private fun clampExtrusion(value: Float, shape: Shape): Float {
        val dimension=minOf(shape.width,shape.height,shape.length)
        return value.coerceIn(-0.4f*dimension,2.0f*dimension)
    }

    private fun updateAreaButtons() {
        if(!::regionRectButton.isInitialized) return
        regionRectButton.text=if(regionKind==0) "● RECTANGLE" else "▭ RECTANGLE"
        regionOvalButton.text=if(regionKind==1) "● OVAL" else "◯ OVAL"
        regionCurveButton.text=if(regionKind==2) "● CURVE" else "〰 CURVE"
    }

    private fun syncRegionUi(face:Int) {
        val shape=currentShape ?: return
        if(face !in 0..5) return
        val area=shape.areas[face]
        regionKind=if(area.kind<0) 0 else area.kind
        updateAreaButtons()
        regionWidthSlider.progress=(area.width*100).toInt()-15
        regionHeightSlider.progress=(area.height*100).toInt()-15
        regionOffsetUSlider.progress=(area.offsetU*100).toInt()+80
        regionOffsetVSlider.progress=(area.offsetV*100).toInt()+80
        extrudeDepthField.setText(String.format(java.util.Locale.US,"%.2f",
            if(area.kind<0) 0.25f else area.depth))
        faceOverlay.invalidate()
    }

    private fun areaFromControls(depth:Float):FaceArea =
        FaceArea(regionKind,
            (regionWidthSlider.progress+15)/100f,
            (regionHeightSlider.progress+15)/100f,
            (regionOffsetUSlider.progress-80)/100f,
            (regionOffsetVSlider.progress-80)/100f,
            depth,
            if(regionKind==2) {
                edgeLiveArea?.curves ?: selectedFace?.let { currentShape?.areas?.get(it) }
                    ?.takeIf { it.kind==2 }?.curves ?: List(4) { 0f }
            } else List(4) { 0f })

    private fun applyFaceInput() {
        val id=selectedFace ?: run { toast("Tap a box face before extruding."); return }
        val depth=extrudeDepthField.text.toString().toFloatOrNull()
        if(depth==null || !depth.isFinite()) {
            toast("Enter depth in metres, such as 0.25 or -0.15.")
            return
        }
        applyFaceExtrusion(id,depth)
    }

    private fun nudgeFace(delta: Float) {
        val id=selectedFace ?: run { toast("Select a face first."); return }
        val shape=currentShape ?: return
        val area=shape.areas[id]
        applyFaceExtrusion(id,clampExtrusion((if(area.kind>=0) area.depth else 0f)+delta,shape))
    }

    private fun removeFaceEdit() {
        val face=selectedFace ?: run { toast("Select a face to remove its edit.");return }
        commitRegion(face,FaceArea())
    }

    private fun applyFaceExtrusion(face:Int,depth:Float) {
        val original=currentShape ?: return
        if(face !in 0..5 || !depth.isFinite()) return
        val safe=clampExtrusion(depth,original)
        if(kotlin.math.abs(safe-depth)>0.0001f) {
            toast("Depth outside safe range; try " +
                String.format(java.util.Locale.US,"%.2f",safe) + " m")
            return
        }
        commitRegion(face,areaFromControls(safe))
    }

    /** Return true only when the selected area is actually in the native project. */
    private fun commitRegion(face:Int,area:FaceArea):Boolean {
        val original=currentShape ?: return false
        if(face !in 0..5) return false
        if(original.areas[face]==area) return true
        val updated=original.areas.toMutableList()
        updated[face]=area
        val next=original.copy(areas=updated)
        var committed=false
        try {
            NativeProject.setFaceRegion(face,area.kind,area.width,area.height,
                area.offsetU,area.offsetV,area.depth,area.curves.toFloatArray())
            committed=true
            if(!renderShape(next)) {
                NativeProject.undo()
                renderShape(original)
                toast("Face preview failed; previous geometry restored.")
                return false
            }
            saveWorkingShape()
            syncRegionUi(face)
            syncShapeUi()
            status.text=faceName(face) +
                if(area.kind<0) ": edit removed; other faces preserved"
                else ": " + String.format(java.util.Locale.US,"%+.2f m",area.depth) +
                     " • " + (when(area.kind){0->"rectangle";1->"oval";else->"curved"}) +
                     " • other faces preserved"
            return true
        } catch(error:Exception) {
            if(committed) {
                try { NativeProject.undo(); renderShape(original) }
                catch(rollback:Exception) {
                    Log.e("ModelForgeFace","Rollback failed",rollback)
                }
            }
            Log.e("ModelForgeFace","Region rejected",error)
            status.text="Area not applied • original shape preserved"
            toast("Area not applied: " + (error.message ?: "invalid geometry"))
            return false
        }
    }

    private fun handleFaceGesture(event: MotionEvent): Boolean {
        if(!faceMode || currentShape==null || !::faceOverlay.isInitialized) return false
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                faceTouchStartX=event.x
                faceTouchStartY=event.y
                if(drawAreaMode) {
                    val face=pickFace(event.x,event.y) ?: return false
                    val uv=screenFaceUv(face,event.x,event.y) ?: return false
                    drawingFace=face
                    selectedFace=face
                    drawStartUv=uv
                    drawPreview=drawnArea(uv,uv)
                    regionKind=2
                    updateAreaButtons()
                    syncShapeUi()
                    faceOverlay.invalidate()
                    status.text="DRAW AREA: drag to opposite corner"
                    return true
                }
                val existing=selectedFace
                if(existing!=null) {
                    val edge=faceOverlay.hitCurveEdge(event.x,event.y)
                    if(edge>=0) {
                        val uv=screenFaceUv(existing,event.x,event.y) ?: return false
                        edgeDragIndex=edge
                        edgeStartUv=uv
                        val shape=currentShape ?: return false
                        val previous=shape.areas[existing]
                        edgeStartArea=areaFromControls(if(previous.kind>=0) previous.depth else 0f)
                            .copy(kind=2)
                        edgeLiveArea=edgeStartArea
                        faceOverlay.invalidate()
                        status.text="BEND EDGE " + (edge+1) + ": drag cyan point"
                        return true
                    }
                }
                faceDragHandle=existing!=null && faceOverlay.hitPull(event.x,event.y)
                if(faceDragHandle) {
                    val shape=currentShape ?: return false
                    faceDragBase=shape.areas[existing ?: return false].depth
                    faceDragPreview=faceDragBase
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if(drawAreaMode && drawingFace>=0) {
                    val startUv=drawStartUv ?: return true
                    val endUv=screenFaceUv(drawingFace,event.x,event.y) ?: return true
                    drawPreview=drawnArea(startUv,endUv)
                    faceOverlay.invalidate()
                    val preview=drawPreview ?: return true
                    status.text=faceName(drawingFace)+": " +
                        (preview.width*100).toInt() + "% × " +
                        (preview.height*100).toInt() + "% • release to keep"
                    return true
                }
                if(edgeDragIndex>=0) {
                    val face=selectedFace ?: return true
                    val beginUv=edgeStartUv ?: return true
                    val uv=screenFaceUv(face,event.x,event.y) ?: return true
                    val base=edgeStartArea ?: return true
                    val movement=FaceUv(uv.u-beginUv.u,uv.v-beginUv.v)
                    edgeLiveArea=bendArea(base,edgeDragIndex,movement)
                    faceOverlay.invalidate()
                    status.text="EDGE " + (edgeDragIndex+1) + ": bend " +
                        String.format(java.util.Locale.US,"%+.2f",
                            edgeLiveArea?.curves?.get(edgeDragIndex) ?: 0f)
                    return true
                }
                if(!faceDragHandle) return false
                val shape=currentShape ?: return true
                val dy=faceTouchStartY-event.y
                val dimension=minOf(shape.width,shape.height,shape.length)
                faceDragPreview=clampExtrusion(faceDragBase +
                    (dy/dp(170).toFloat())*(dimension*0.8f),shape)
                status.text="EXTRUDE " + faceName(selectedFace ?: -1) + "  " +
                    String.format(java.util.Locale.US,"%+.2f m",faceDragPreview) +
                    " • release to apply"
                return true
            }
            MotionEvent.ACTION_UP -> {
                if(drawAreaMode && drawingFace>=0) {
                    val face=drawingFace
                    val startUv=drawStartUv
                    val endUv=screenFaceUv(face,event.x,event.y)
                    val dx=event.x-faceTouchStartX
                    val dy=event.y-faceTouchStartY
                    val region=if(startUv!=null && endUv!=null)
                        drawnArea(startUv,endUv) else drawPreview
                    drawAreaMode=false
                    drawingFace=-1
                    drawStartUv=null
                    drawPreview=null
                    edgeLiveArea=null
                    if(region!=null && dx*dx+dy*dy>dp(25)*dp(25)) {
                        selectedFace=face
                        regionKind=2
                        if(commitRegion(face,region.copy(kind=2,depth=0f))) {
                            status.text="Area saved • bend cyan edges • PULL to shape"
                        }
                    } else {
                        status.text="Area too small • touch and drag farther to size"
                    }
                    syncShapeUi()
                    faceOverlay.invalidate()
                    return true
                }
                if(edgeDragIndex>=0) {
                    val face=selectedFace
                    val target=edgeLiveArea
                    edgeDragIndex=-1
                    edgeStartUv=null
                    edgeStartArea=null
                    edgeLiveArea=null
                    if(face!=null && target!=null) {
                        regionKind=2
                        commitRegion(face,target)
                    }
                    faceOverlay.invalidate()
                    return true
                }
                if(faceDragHandle) {
                    faceDragHandle=false
                    selectedFace?.let { applyFaceExtrusion(it,faceDragPreview) }
                    return true
                }
                val dx=event.x-faceTouchStartX
                val dy=event.y-faceTouchStartY
                if(event.pointerCount==1 && dx*dx+dy*dy<=dp(14)*dp(14)) {
                    selectedFace=pickFace(event.x,event.y)
                    drawPreview=null
                    edgeLiveArea=null
                    val face=selectedFace
                    if(face!=null) {
                        syncRegionUi(face)
                        status.text=faceName(face)+
                            " selected • DRAW AREA or bend cyan edge handles"
                    } else status.text="No face found • tap a solid visible surface"
                    syncShapeUi()
                }
                return false // Pass camera release through unchanged.
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                val captured=(drawingFace>=0 || edgeDragIndex>=0 || faceDragHandle)
                drawingFace=-1
                drawStartUv=null
                drawPreview=null
                edgeDragIndex=-1
                edgeStartUv=null
                edgeStartArea=null
                edgeLiveArea=null
                faceDragHandle=false
                if(captured) faceOverlay.invalidate()
                return captured
            }
            else -> return drawingFace>=0 || edgeDragIndex>=0 || faceDragHandle
        }
    }

    private inner class FaceSelectionOverlay : View(this@MainActivity) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private fun selectedPoints(): List<ScreenPoint>? {
            if(!faceMode) return null
            val shape=currentShape ?: return null
            val id=selectedFace ?: return null
            val packed=currentGeneratedMesh ?: return null
            val base=baseGeneratedMesh ?: return null
            if(!::viewer.isInitialized || !::regionWidthSlider.isInitialized) return null
            val mapper=MeshProjector(packed)
            if(!mapper.visibleFace(id,shape)) return null
            val start=id*36
            if(start+35>=base.size) return null
            val ids=intArrayOf(0,1,2,5)
            val outer=ids.map { vi ->
                FloatArray(3) { axis -> base[start+vi*6+axis] }
            }
            val feature=drawPreview ?: edgeLiveArea ?: areaFromControls(0f)
            val cu=0.5f+feature.offsetU*(1-feature.width)*0.5f
            val cv=0.5f+feature.offsetV*(1-feature.height)*0.5f
            val depth=if(drawPreview!=null) 0f else shape.areas[id].depth
            val nx=base[start+3]
            val ny=base[start+4]
            val nz=base[start+5]
            fun facePoint(u:Float,v:Float):ScreenPoint? {
                val wt=floatArrayOf((1-u)*(1-v),u*(1-v),u*v,(1-u)*v)
                var x=0f;var y=0f;var z=0f
                for(k in 0..3) {
                    x+=outer[k][0]*wt[k]
                    y+=outer[k][1]*wt[k]
                    z+=outer[k][2]*wt[k]
                }
                return mapper.atWorld(x+nx*depth,y+ny*depth,z+nz*depth)
            }
            if(feature.kind==0) {
                return listOf(
                    facePoint(cu-feature.width*0.5f,cv-feature.height*0.5f),
                    facePoint(cu+feature.width*0.5f,cv-feature.height*0.5f),
                    facePoint(cu+feature.width*0.5f,cv+feature.height*0.5f),
                    facePoint(cu-feature.width*0.5f,cv+feature.height*0.5f)
                ).map { it ?: return null }
            }
            if(feature.kind==2) {
                val cornersUv=arrayOf(
                    FaceUv(cu-feature.width*0.5f,cv-feature.height*0.5f),
                    FaceUv(cu+feature.width*0.5f,cv-feature.height*0.5f),
                    FaceUv(cu+feature.width*0.5f,cv+feature.height*0.5f),
                    FaceUv(cu-feature.width*0.5f,cv+feature.height*0.5f)
                )
                val result=ArrayList<ScreenPoint>(32)
                for(side in 0..3) {
                    val a=cornersUv[side]
                    val b=cornersUv[(side+1)%4]
                    val inwardU=when(side){1->-1f;3->1f;else->0f}
                    val inwardV=when(side){0->1f;2->-1f;else->0f}
                    val amount=2f*feature.curves[side]*minOf(feature.width,feature.height)
                    val ctrlU=(a.u+b.u)*0.5f+inwardU*amount
                    val ctrlV=(a.v+b.v)*0.5f+inwardV*amount
                    for(step in 0 until 8) {
                        val t=step/8f
                        val one=1f-t
                        val u=one*one*a.u+2f*one*t*ctrlU+t*t*b.u
                        val v=one*one*a.v+2f*one*t*ctrlV+t*t*b.v
                        result.add(facePoint(u,v) ?: return null)
                    }
                }
                return result
            }
            val tau=2.0*Math.PI
            val cornerUv=arrayOf(
                floatArrayOf(0f,0f),floatArrayOf(1f,0f),
                floatArrayOf(1f,1f),floatArrayOf(0f,1f))
            val angles=DoubleArray(5)
            for(k in 0..3) {
                var theta=kotlin.math.atan2((cornerUv[k][1]-cv).toDouble(),
                                            (cornerUv[k][0]-cu).toDouble())
                if(k>0) while(theta<=angles[k-1]) theta+=tau
                angles[k]=theta
            }
            angles[4]=angles[0]+tau
            val result=ArrayList<ScreenPoint>(32)
            for(side in 0..3) for(step in 0 until 8) {
                val angle=angles[side]+(angles[side+1]-angles[side])*step/8.0
                val u=cu+feature.width*0.5f*kotlin.math.cos(angle).toFloat()
                val v=cv+feature.height*0.5f*kotlin.math.sin(angle).toFloat()
                result.add(facePoint(u,v) ?: return null)
            }
            return result
        }
        private fun center(): PointF? {
            val p=selectedPoints() ?: return null
            return PointF(p.map { it.x }.average().toFloat(),
                p.map { it.y }.average().toFloat())
        }
        fun hitCurveEdge(x:Float,y:Float):Int {
            if(drawAreaMode || regionKind!=2) return -1
            val outline=selectedPoints() ?: return -1
            if(outline.size!=32) return -1
            var best=-1
            var distance=dp(30).toFloat()*dp(30)
            for(side in 0..3) {
                val midpoint=outline[side*8+4]
                val dx=midpoint.x-x
                val dy=midpoint.y-y
                val square=dx*dx+dy*dy
                if(square<distance) {
                    distance=square
                    best=side
                }
            }
            return best
        }
        fun hitPull(x: Float,y: Float): Boolean {
            val p=center() ?: return false
            val dx=x-p.x
            val dy=y-p.y
            return dx*dx+dy*dy<dp(44).toFloat()*dp(44)
        }
        override fun onDraw(canvas: Canvas) {
            val p=selectedPoints() ?: return
            val path=Path().apply {
                moveTo(p[0].x,p[0].y)
                for(i in 1 until p.size) lineTo(p[i].x,p[i].y)
                close()
            }
            paint.style=Paint.Style.FILL
            paint.color=Color.argb(66,255,199,80)
            canvas.drawPath(path,paint)
            paint.style=Paint.Style.STROKE
            paint.strokeWidth=dp(3).toFloat()
            paint.color=Color.rgb(255,209,91)
            canvas.drawPath(path,paint)
            if(drawAreaMode) {
                paint.style=Paint.Style.FILL
                paint.color=Color.rgb(255,210,96)
                for(index in listOf(0,p.size/4,p.size/2,p.size*3/4)) {
                    canvas.drawCircle(p[index].x,p[index].y,dp(7).toFloat(),paint)
                }
                return
            }
            if(regionKind==2 && p.size==32) {
                paint.style=Paint.Style.FILL
                for(side in 0..3) {
                    val midpoint=p[side*8+4]
                    paint.color=Color.rgb(22,52,66)
                    canvas.drawCircle(midpoint.x,midpoint.y,dp(15).toFloat(),paint)
                    paint.color=Color.rgb(54,212,188)
                    canvas.drawCircle(midpoint.x,midpoint.y,dp(10).toFloat(),paint)
                }
            }
            val x=p.map { it.x }.average().toFloat()
            val y=p.map { it.y }.average().toFloat()
            paint.style=Paint.Style.FILL
            paint.color=Color.WHITE
            canvas.drawCircle(x,y,dp(21).toFloat(),paint)
            paint.color=Color.rgb(26,42,56)
            canvas.drawCircle(x,y,dp(9).toFloat(),paint)
            paint.color=Color.WHITE
            paint.textAlign=Paint.Align.CENTER
            paint.textSize=dp(12).toFloat()
            paint.setShadowLayer(dp(2).toFloat(),0f,0f,Color.BLACK)
            canvas.drawText("PULL",x,y-dp(33),paint)
            paint.clearShadowLayer()
        }
    }

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
            // Use current exported mesh bounds, including any extrusion,
            // to match ModelViewer.transformToUnitCube exactly.
            val packed = currentGeneratedMesh ?: return emptyList()
            val lo=DoubleArray(3) { Double.POSITIVE_INFINITY }
            val hi=DoubleArray(3) { Double.NEGATIVE_INFINITY }
            for(i in packed.indices step 6) {
                for(axis in 0..2) {
                    lo[axis]=kotlin.math.min(lo[axis],packed[i+axis].toDouble())
                    hi[axis]=kotlin.math.max(hi[axis],packed[i+axis].toDouble())
                }
            }
            val scale=2.0/maxOf(hi[0]-lo[0],hi[1]-lo[1],hi[2]-lo[2])
            val centerX=(lo[0]+hi[0])*0.5
            val centerY=(lo[1]+hi[1])*0.5
            val centerZ=(lo[2]+hi[2])*0.5
            val topY=(h/2.0-centerY)*scale
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
