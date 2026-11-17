package com.modelforge.nativeeditor

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

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
    private lateinit var pinch: ScaleGestureDetector
    private var currentGlb: ByteArray? = null
    private var currentName = "Starter Box"
    private var importedPreview = false
    private var running = false
    private var distance = 3.3
    private var azimuth = 0.55
    private var elevation = 0.32
    private var lastFrameNs = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var pinchActive = false
    private var backgroundFill: IndirectLight? = null
    private val studioLights = mutableListOf<Int>()
    private val openCode = 401
    private val exportCode = 402

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        Gltfio.init()
        createInterface()
        viewer = ModelViewer(
            surface, Engine.create(),
            UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK), null
        )
        setupLighting()
        viewer.camera.setExposure(13.0f, 1.0f / 125.0f, 100.0f)
        pinch = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                distance = (distance / detector.scaleFactor.toDouble()).coerceIn(1.2, 18.0)
                updateCamera()
                pinchActive = true
                return true
            }
        })
        surface.setOnTouchListener { _, event -> orbitTouch(event) }
        updateCamera()
        createBox()
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

        val actions = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val actionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionsRow)
        drawer.addView(actions)
        actionsRow.addView(button("CREATE BOX") { createBox() })
        actionsRow.addView(button("FIT VIEW") {
            azimuth = 0.55; elevation = 0.32; distance = 3.3; updateCamera()
        })
        actionsRow.addView(button("ADVANCED • SOON") {
            toast("Precise face/edge modeling is the next milestone; this version is the native foundation.")
        })
        drawer.addView(textLabel("Offline-first • GLB export • Community sharing planned, not active", 11f, muted))
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

    private fun setDrawerVisible(opened: Boolean) {
        drawer.visibility = if (opened) View.VISIBLE else View.GONE
        drawerToggle.text = if (opened) "▼ HIDE TOOLS" else "▲ OPEN TOOLS"
        status.text = if (opened) "Right joystick: orbit • pinch: zoom • drag: rotate"
                      else "Full view • ▲ OPEN TOOLS to continue modeling"
    }

    private fun createBox() {
        try {
            val w = widthField.text.toString().toFloat()
            val h = heightField.text.toString().toFloat()
            val l = lengthField.text.toString().toFloat()
            val bytes = GlbBuilder.box(w, h, l)
            present(bytes, "Box " + w + " × " + h + " × " + l, false)
        } catch (error: Exception) {
            toast("Enter valid positive dimensions (0.01–10000): " + (error.message ?: "invalid"))
        } catch (error: UnsatisfiedLinkError) {
            toast("Native modeling library missing; check APK install.")
        }
    }

    private fun present(bytes: ByteArray, name: String, imported: Boolean) {
        if (!GlbBuilder.isValidHeader(bytes)) {
            toast("Invalid GLB format; model was not replaced.")
            return
        }
        try {
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put(bytes)
            buffer.flip()
            viewer.loadModelGlb(buffer)
            viewer.transformToUnitCube(Float3(0f, 0f, 0f))
            currentGlb = bytes
            currentName = name
            importedPreview = imported
            azimuth = 0.55; elevation = 0.32; distance = 3.3
            updateCamera()
            status.text = if (imported) "GLB preview: " + name + " • Import editing coming later"
                          else name + " • C++ geometry • PBR viewport"
        } catch (error: Exception) {
            toast("GLB could not render: " + (error.message ?: "unsupported asset"))
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

    private fun updateCamera() {
        if (!::viewer.isInitialized) return
        val x = distance * cos(elevation) * sin(azimuth)
        val y = distance * sin(elevation)
        val z = distance * cos(elevation) * cos(azimuth)
        viewer.camera.lookAt(x, y, z, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
    }

    private fun orbitTouch(event: MotionEvent): Boolean {
        pinch.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; pinchActive = false }
            MotionEvent.ACTION_POINTER_DOWN -> { pinchActive = true }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !pinch.isInProgress && !pinchActive) {
                    azimuth -= (event.x - lastX) * 0.007
                    elevation = (elevation + (event.y - lastY) * 0.007).coerceIn(-1.43, 1.43)
                    updateCamera()
                }
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { pinchActive = false }
        }
        return true
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (lastFrameNs > 0L) {
            val dt = ((frameTimeNanos - lastFrameNs).toDouble() * 1e-9).coerceIn(0.0, 0.05)
            if (orbitPad.dx != 0f || orbitPad.dy != 0f) {
                azimuth -= orbitPad.dx * dt * 1.8
                elevation = (elevation - orbitPad.dy * dt * 1.45).coerceIn(-1.43, 1.43)
                updateCamera()
            }
        }
        lastFrameNs = frameTimeNanos
        viewer.render(frameTimeNanos)
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
                present(bytes, name, true)
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
                "CREATE: Set width, height and length and tap CREATE BOX. Geometry comes from a native C++ modeling kernel.\n\n" +
                "VIEW: Drag or use the right pad to orbit. Pinch to zoom. ▼ HIDE TOOLS expands the screen.\n\n" +
                "SAVE GLB: Keep the real mesh on your phone. OPEN GLB: Preview existing models.\n\n" +
                "COMING NEXT: Editable face/edge topology, bevel, extrusion, full project history and optional community. Imported GLBs are preview-only in this build."
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
        orbitPad.dx = 0f; orbitPad.dy = 0f
        super.onPause()
    }
    override fun onDestroy() {
        running = false
        if (::viewer.isInitialized) {
            try { viewer.destroyModel() } catch (_: Exception) { }
        }
        super.onDestroy()
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
