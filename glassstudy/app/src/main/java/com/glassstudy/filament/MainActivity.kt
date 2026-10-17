package com.glassstudy.filament

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.filament.Camera
import com.google.android.filament.Skybox
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.GlassStudyModelViewer
import com.google.android.filament.utils.Utils
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Native Filament beta. Renderer and glass geometry are the SAME glTF mesh.
 * No v0.1.x SDF approximation is used anywhere in this app.
 * Exported scene contains the very same GLB that gltfio sends to Filament.
 */
class MainActivity : Activity() {
    companion object {
        init { Utils.init() }
        private const val EXPORT_SCENE = 1201
        private val ids = arrayOf("faceted", "tumbler", "wine", "mug", "pitcher", "bowl", "vase", "sphere", "cube", "cylinder", "cone")
        private val names = arrayOf("Гранёный стакан", "Стакан", "Бокал", "Чашка", "Кувшин", "Пиала", "Ваза", "Сфера", "Куб", "Цилиндр", "Конус")
        private val qualities = arrayOf("low", "medium", "high")
    }
    private lateinit var surface: SurfaceView
    private lateinit var viewer: GlassStudyModelViewer
    private lateinit var updater: PatchManager
    private var wireframe = false
    private lateinit var label: TextView
    private lateinit var topPanel: LinearLayout
    private lateinit var bottomPanel: LinearLayout
    private lateinit var selectorRow: LinearLayout
    private lateinit var actionsRow: LinearLayout
    private lateinit var headerSubtitle: TextView
    private lateinit var headerRow: LinearLayout
    private lateinit var footerActions: LinearLayout
    private lateinit var fpsPill: TextView
    private lateinit var quickObjects: Button
    private lateinit var quickLight: Button
    private lateinit var quickReset: Button
    private lateinit var quickMenu: Button
    private lateinit var cameraButton: Button
    private lateinit var gridButton: Button
    private lateinit var exportButton: Button
    private lateinit var diagnosticButton: Button
    private var zoomPower = .50
    private var diagnosticLabels: Map<String,String> = emptyMap()
    private var diagnosticSections: List<String> =
        listOf("updates","performance","memory","scene","renderer","camera","device")
    private val profiler = FrameProfiler()
    private lateinit var chooseObject: Spinner
    private lateinit var chooseLod: Spinner
    private var currentId = "tumbler"
    private var currentLod = "medium"
    private var lightMode = false
    // Stable orbit defaults and elevation bounds adapted from GlassStudy v0.1.22.
    private var yaw = .56
    private var pitch = .34
    private var distance = 5.5
    private var lightAz = 1.3
    private var lightElevation = .9
    private var brightness = 1.0
    private var oldX = 0f
    private var oldY = 0f
    private var oldPinch = 0f
    private var running = false
    private var stableFrames = 0
    private var localPatchBusy = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            Choreographer.getInstance().postFrameCallback(this)
            val submitStartNs=SystemClock.elapsedRealtimeNanos()
            updateCamera()
            viewer.render(frameTimeNanos)
            val submissionMs=(SystemClock.elapsedRealtimeNanos()-submitStartNs)/1_000_000.0
            profiler.frame(frameTimeNanos,submissionMs)
            if (::fpsPill.isInitialized && viewer.renderedFrames % 40L == 0L) {
                val f=profiler.metrics().fps
                fpsPill.text="${if(f!=null) String.format(Locale.US,"%.0f",f) else "—"} FPS · Filament"
            }
            if (++stableFrames == 4) updater.confirmFrameRendered()
        }
    }

    private fun d(v: Float): Int = (v * resources.displayMetrics.density).toInt()
    private val cyan = Color.rgb(109, 224, 222)
    private val paleText = Color.rgb(232, 247, 251)

    // v0.1.22 glass lab palette: graphite #07111d, deep blue #10273b,
    // cyan #6ad8db, translucent surfaces, outlined rounded buttons.
    private fun roundedFill(color:Int, radius:Float=15f, outline:Int=cyan, stroke:Float=1f):GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(color, Color.rgb(16,39,58))).apply {
            cornerRadius=d(radius).toFloat()
            setStroke(d(stroke).coerceAtLeast(1), outline)
        }
    }
    private fun skin(button: Button, selected:Boolean=false, color:Int=Color.rgb(27,56,75)) {
        val accent=if(selected) Color.rgb(48,116,124) else color
        val outline=if(selected) Color.rgb(166,246,233) else Color.argb(180,109,185,203)
        val gradient=roundedFill(accent,14f,outline)
        button.background=RippleDrawable(ColorStateList.valueOf(Color.argb(115,93,230,222)),gradient,null)
        button.setTextColor(paleText)
        button.elevation=d(2f).toFloat()
    }
    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize=14f
        setAllCaps(false)
        minimumWidth=0
        minimumHeight=0
        minWidth=0
        minHeight=0
        setPadding(d(7f),0,d(7f),0)
        skin(this)
        setOnClickListener { action() }
    }
    private fun shortcut(text: String, tip:String, action: () -> Unit): Button =
        button(text,action).apply {
            textSize=23f
            contentDescription=tip
            setPadding(0,0,0,0)
            layoutParams=LinearLayout.LayoutParams(d(39f),d(43f))
        }
    private fun selectionAdapter(values:Array<String>):ArrayAdapter<String> {
        val adapter=object: ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values) {
            override fun getView(position:Int, convertView:View?, parent:ViewGroup):View {
                val label=super.getView(position,convertView,parent) as TextView
                label.text="${values[position]}  ▾"
                label.textSize=14f
                label.setSingleLine(true)
                label.setTextColor(paleText)
                label.setPadding(d(12f),0,d(7f),0)
                return label
            }
            override fun getDropDownView(position:Int, convertView:View?,parent:ViewGroup):View {
                val label=super.getDropDownView(position,convertView,parent) as TextView
                label.text=values[position]
                label.textSize=16f
                label.setTextColor(paleText)
                label.setPadding(d(15f),d(10f),d(9f),d(10f))
                label.setBackgroundColor(Color.rgb(24,48,62))
                return label
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }
    private fun openMenu() {
        AlertDialog.Builder(this).setTitle("GlassStudy · 3D LAB")
            .setItems(arrayOf("Выбрать объект", "Сбросить камеру", "Экспорт сцены", "Диагностика")) { _,which ->
                when(which) {
                    0 -> chooseObject.performClick()
                    1 -> resetCameraToHome()
                    2 -> exportScene()
                    3 -> showDiagnostic()
                }
            }.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updater = PatchManager(this)
        updater.reconcileAfterCrash()
        wireframe = getPreferences(MODE_PRIVATE).getBoolean("wireframe", false)
        window.statusBarColor = Color.rgb(39, 48, 55)
        window.navigationBarColor = Color.rgb(31, 41, 49)
        surface = SurfaceView(this)
        val root = FrameLayout(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        // Android 15+ edge-to-edge: keep controls out of the status and navigation bars.
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            root.requestApplyInsets()
        } else {
            root.fitsSystemWindows = true
        }

        viewer = GlassStudyModelViewer(surface, manipulator = null)
        viewer.scene.skybox = Skybox.Builder().color(.43f, .43f, .43f, 1f).build(viewer.engine)
        // Filament uses glTF PBR. Proper specular IBL appears when cmgen-produced studio_ibl.ktx is packaged.
        try {
            val bytes = assets.open("envs/studio_ibl.ktx").use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes).flip()
            viewer.scene.indirectLight = KTX1Loader.createIndirectLight(viewer.engine, buf).also { it.intensity = 30_000f }
        } catch (_: java.io.FileNotFoundException) {
            // Development fallback: built-in directional light from ModelViewer, no fabricated HDR lighting.
        } catch (e: Exception) {
            android.util.Log.w("GlassStudy", "Optional IBL could not load", e)
        }
        viewer.camera.setExposure(16f, 1f / 125f, 100f)
        viewer.view.antiAliasing = com.google.android.filament.View.AntiAliasing.FXAA
        viewer.view.ambientOcclusionOptions = viewer.view.ambientOcclusionOptions.apply { enabled = true }
        // Scene and view resize are owned by ModelViewer, gestures by this Activity.
        surface.setOnTouchListener { _, ev -> touch(ev) }

        // Floating header, modeled on GlassStudy 3D LAB v0.1.22.
        topPanel = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(d(10f),d(9f),d(10f),d(7f))
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(195,24,34,44),Color.argb(12,24,34,44)))
        }
        headerRow=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val logo=TextView(this).apply {
            text="◇"
            textSize=34f
            gravity=Gravity.CENTER
            setTextColor(cyan)
        }
        headerRow.addView(logo, LinearLayout.LayoutParams(d(34f),d(48f)))
        val branding=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val brandTitle=TextView(this).apply {
            text="GlassStudy"
            textSize=19f
            setTextColor(Color.WHITE)
            setSingleLine(true)
        }
        headerSubtitle=TextView(this).apply {
            text="3D LAB · v${BuildConfig.VERSION_NAME}"
            textSize=9f
            setTextColor(Color.rgb(133,197,210))
            letterSpacing=.18f
            setSingleLine(true)
        }
        branding.addView(brandTitle)
        branding.addView(headerSubtitle)
        headerRow.addView(branding, LinearLayout.LayoutParams(0,d(48f),1f))
        quickObjects=shortcut("▣","Выбрать объект") { chooseObject.performClick() }
        quickLight=shortcut("☼","Освещение / камера") {
            lightMode=!lightMode
            status()
        }
        quickReset=shortcut("↶","Сброс камеры") { resetCameraToHome() }
        quickMenu=shortcut("☷","Меню") { openMenu() }
        for(b in listOf(quickObjects,quickLight,quickReset,quickMenu)) {
            val lp=LinearLayout.LayoutParams(d(39f),d(43f))
            lp.leftMargin=d(4f)
            headerRow.addView(b,lp)
        }
        topPanel.addView(headerRow)
        label=TextView(this).apply {
            textSize=11f
            gravity=Gravity.CENTER
            setTextColor(Color.rgb(220,238,242))
            setPadding(0,d(7f),0,d(2f))
            setSingleLine(true)
        }
        topPanel.addView(label)
        root.addView(topPanel, FrameLayout.LayoutParams(-1,-2,Gravity.TOP))

        // Bottom controls float above the viewport; no "Применить" button.
        val dock=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(d(9f),0,d(9f),d(9f))
        }
        fpsPill=TextView(this).apply {
            text="— FPS · Filament"
            textSize=11f
            setTextColor(Color.rgb(147,219,219))
            setPadding(d(13f),d(6f),d(13f),d(6f))
            background=roundedFill(Color.rgb(19,41,54),30f,Color.argb(130,109,185,203))
        }
        val pillParams=LinearLayout.LayoutParams(-2,-2)
        pillParams.bottomMargin=d(6f)
        dock.addView(fpsPill,pillParams)

        bottomPanel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(d(11f),d(11f),d(11f),d(12f))
            background=roundedFill(Color.rgb(35,47,58),19f,Color.argb(120,177,218,225))
            elevation=d(6f).toFloat()
        }
        selectorRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        chooseObject=Spinner(this).apply {
            adapter=selectionAdapter(names)
            background=roundedFill(Color.rgb(20,42,57),13f,Color.argb(152,126,172,190))
        }
        val first=LinearLayout.LayoutParams(0,d(45f),1.35f)
        first.rightMargin=d(7f)
        selectorRow.addView(chooseObject,first)
        chooseLod=Spinner(this).apply {
            adapter=selectionAdapter(arrayOf("Low","Medium","High"))
            background=roundedFill(Color.rgb(20,42,57),13f,Color.argb(152,126,172,190))
        }
        selectorRow.addView(chooseLod,LinearLayout.LayoutParams(0,d(45f),1f))
        bottomPanel.addView(selectorRow)

        actionsRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val aLp=LinearLayout.LayoutParams(-1,d(45f))
        aLp.topMargin=d(8f)
        bottomPanel.addView(actionsRow,aLp)
        cameraButton=button("☼  Свет / Камера") {
            lightMode=!lightMode
            status()
        }
        cameraButton.setOnLongClickListener {
            resetCameraToHome()
            Toast.makeText(this,"Камера сброшена",Toast.LENGTH_SHORT).show()
            true
        }
        val camLp=LinearLayout.LayoutParams(0,-1,1.25f)
        camLp.rightMargin=d(7f)
        actionsRow.addView(cameraButton,camLp)
        gridButton=button("▦  Сетка") {
            wireframe=!wireframe
            getPreferences(MODE_PRIVATE).edit().putBoolean("wireframe",wireframe).apply()
            loadSelected()
            status()
        }
        actionsRow.addView(gridButton,LinearLayout.LayoutParams(0,-1,.78f))
        footerActions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val div=View(this).apply { setBackgroundColor(Color.argb(75,181,220,223)) }
        val divLp=LinearLayout.LayoutParams(-1,d(1f))
        divLp.topMargin=d(10f)
        divLp.bottomMargin=d(9f)
        bottomPanel.addView(div,divLp)
        bottomPanel.addView(footerActions,LinearLayout.LayoutParams(-1,d(48f)))
        exportButton=button("⇧  Экспорт сцены") { exportScene() }
        val eLp=LinearLayout.LayoutParams(0,-1,1f)
        eLp.rightMargin=d(7f)
        footerActions.addView(exportButton,eLp)
        diagnosticButton=button("▤  Диагностика") { showDiagnostic() }
        diagnosticButton.textSize=12f
        footerActions.addView(diagnosticButton,LinearLayout.LayoutParams(0,-1,1.13f))
        dock.addView(bottomPanel)
        root.addView(dock,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))

        chooseObject.setSelection(1)
        chooseLod.setSelection(1)
        applyUiLayout()
        loadSelected()
        // Immediately reload when a model or LOD is chosen, replacing "Применить".
        val onSelection=object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?, view:View?, position:Int, id:Long) {
                if (::viewer.isInitialized && ::updater.isInitialized &&
                    (ids[chooseObject.selectedItemPosition.coerceIn(0,ids.lastIndex)] != currentId ||
                    qualities[chooseLod.selectedItemPosition.coerceIn(0,2)] != currentLod)) {
                    loadSelected()
                }
            }
            override fun onNothingSelected(parent:AdapterView<*>?) {}
        }
        chooseObject.onItemSelectedListener=onSelection
        chooseLod.onItemSelectedListener=onSelection
        // A user-selected file in Downloads takes priority over the background check.
        if (!handleIncomingPatchIntent(intent)) {
            updater.checkOnStart(onCandidate = { candidate ->
                applyVerifiedPatch(candidate, false)
            })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingPatchIntent(intent)
    }

    /** Accept files opened from Downloads or shared explicitly to GlassStudy.
     * Input is untrusted until PatchManager verifies RSA signature and SHA-256.
     */
    private fun handleIncomingPatchIntent(incoming: Intent?): Boolean {
        val uri: Uri? = when (incoming?.action) {
            Intent.ACTION_VIEW -> incoming.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                incoming.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            else -> null
        }
        if (uri == null) return false
        if (localPatchBusy) return true
        if (uri.scheme != "content" && uri.scheme != "file") {
            android.util.Log.w("GlassStudy", "Unsupported import URI")
            return true
        }
        localPatchBusy = true
        updater.installFromUri(uri,
            onCandidate = { candidate ->
                localPatchBusy = false
                applyVerifiedPatch(candidate, true)
            },
            onError = { reason ->
                localPatchBusy = false
                if (!isFinishing && !isDestroyed) {
                    AlertDialog.Builder(this)
                        .setTitle("Патч не установлен")
                        .setMessage(reason)
                        .setPositiveButton("Закрыть", null).show()
                }
            }
        )
        return true
    }

    /** The same validated staging path is used for local and online updates. */
    private fun applyVerifiedPatch(candidate: PatchManager.Candidate, fromDownloads: Boolean) {
        if (isFinishing || isDestroyed) {
            updater.reject(candidate)
            return
        }
        if (loadSelected(candidate) && applyUiLayout(candidate) && updater.activate(candidate)) {
            stableFrames = 0
            status()
            val message="Патч ${candidate.version} установлен"
            if (fromDownloads) {
                AlertDialog.Builder(this).setTitle("GlassStudy")
                    .setMessage(message).setPositiveButton("OK",null).show()
            } else {
                Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
            }
        } else {
            updater.reject(candidate)
            applyUiLayout()
            loadSelected()
            if (fromDownloads) {
                AlertDialog.Builder(this).setTitle("Патч отклонён")
                    .setMessage("Модель или настройки не прошли проверку. Рабочая версия сохранена.")
                    .setPositiveButton("OK",null).show()
            }
        }
    }

    /** Safe declarative style, still accepted from signed .pupdate version 20023+. */
    private fun applyUiLayout(candidate:PatchManager.Candidate?=null):Boolean {
        try {
            val ui=UiLayout.parse(updater.readUiConfig(candidate))
            zoomPower=ui.zoomPower
            diagnosticLabels=ui.diagnostics
            diagnosticSections=ui.diagnosticSections
            label.textSize=ui.textSizeSp.coerceIn(11f,14f)
            label.setTextColor(ui.textColor)
            // Old patches use #203E4F; apply that to the new rounded, outlined
            // buttons without reverting to Android's old rectangular layout.
            val allButtons=listOf(cameraButton,gridButton,exportButton,diagnosticButton,
                quickObjects,quickLight,quickReset,quickMenu)
            for (b in allButtons) {
                skin(b,b===quickLight && lightMode,ui.buttonColor)
                b.setTextColor(ui.buttonTextColor)
            }
            status()
            exportButton.text="⇧  ${ui.exportText}"
            diagnosticButton.text="▤  ${ui.diagnosticText}"
            return true
        } catch(e:Exception) {
            android.util.Log.w("GlassStudy","Rejected UI patch",e)
            return false
        }
    }

    private fun loadSelected(candidate: PatchManager.Candidate? = null): Boolean {
        currentId = ids[chooseObject.selectedItemPosition.coerceIn(0, ids.lastIndex)]
        currentLod = qualities[chooseLod.selectedItemPosition.coerceIn(0, 2)]
        try {
            viewer.loadModelGlb(modelData(candidate))
            if (viewer.asset == null) throw IllegalStateException("Filament gltfio rejected the GLB")
            status()
            return true
        } catch (e: Exception) {
            if (candidate != null) {
                // Reject bad background patches silently: preserve the working scene.
                android.util.Log.w("GlassStudy", "Staged model failed to render", e)
            } else {
                AlertDialog.Builder(this).setTitle("Ошибка загрузки модели")
                    .setMessage(e.stackTraceToString().take(3000)).setPositiveButton("OK", null).show()
            }
            return false
        }
    }
    private fun modelData(candidate: PatchManager.Candidate? = null): ByteBuffer {
        val name="models/${currentId}_${currentLod}${if(wireframe) "_debug" else ""}.glb"
        val bytes = if(candidate != null) updater.readModel(name,candidate) else updater.readModel(name)
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); flip() }
    }
    private fun resetCameraToHome() {
        yaw = .56
        pitch = .34
        distance = 5.5
        lightMode = false
        status()
        if (::viewer.isInitialized) updateCamera()
    }

    private fun updateCamera() {
        val cy = cos(pitch)
        val x = distance * cy * sin(yaw)
        val z = distance * cy * cos(yaw)
        // Target the center of the glass at y=1, as in GlassStudy v0.1.22.
        val y = 1.0 + distance * sin(pitch)
        viewer.camera.lookAt(x, y, z, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0)
        val aspect = max(.2, surface.width.toDouble() / max(1, surface.height))
        viewer.camera.setProjection(45.0, aspect, 0.05, 125.0, Camera.Fov.VERTICAL)
        val dx = cos(lightElevation) * sin(lightAz)
        val dy = sin(lightElevation)
        val dz = cos(lightElevation) * cos(lightAz)
        val mgr = viewer.engine.lightManager
        val inst = mgr.getInstance(viewer.light)
        mgr.setDirection(inst, (-dx).toFloat(),(-dy).toFloat(),(-dz).toFloat())
        mgr.setIntensity(inst,(100000f * brightness).toFloat())
    }
    private fun touch(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            oldX = e.getX(0); oldY = e.getY(0)
            oldPinch = if (e.pointerCount >= 2) pinch(e) else 0f
            return true
        }
        if (e.actionMasked == MotionEvent.ACTION_MOVE) {
            if (e.pointerCount >= 2) {
                val delta = pinch(e)
                if (oldPinch > 0f && delta > 1f) {
                    if (lightMode) brightness = (brightness * (delta / oldPinch).toDouble().pow(.7)).coerceIn(.05, 3.0)
                    // Spread two fingers => move closer; pinch => farther. Same as v0.1.22.
                    else distance = (distance * (oldPinch / delta).toDouble().pow(zoomPower)).coerceIn(2.7, 60.0)
                }
                oldPinch = delta
            } else {
                // Android touch coordinates are physical pixels; the v0.1.22
                // browser uses CSS px. Convert with density BEFORE applying
                // the original per-CSS-pixel sensitivity coefficients.
                val pxToCss = resources.displayMetrics.density.coerceAtLeast(.1f)
                val dx = (e.x - oldX).toDouble() / pxToCss
                val dy = (e.y - oldY).toDouble() / pxToCss
                if (lightMode) {
                    // Identical to v0.1.22: 0.009/0.006 radians per CSS pixel.
                    lightAz += dx * .009
                    lightElevation = (lightElevation + dy * .006).coerceIn(.06,1.5)
                } else {
                    // Identical to v0.1.22: 0.008/0.005 radians per CSS pixel.
                    yaw += dx * .008
                    pitch = (pitch + dy * .005).coerceIn(-.05,1.23)
                }
            }
            oldX = e.getX(0); oldY = e.getY(0)
            return true
        }
        return true
    }
    private fun pinch(e: MotionEvent): Float {
        val a=e.getX(0)-e.getX(1)
        val b=e.getY(0)-e.getY(1)
        return sqrt(a*a+b*b)
    }
    private fun status() {
        if (::label.isInitialized) {
            val source = if (lightMode) "СВЕТ: палец — направление, два — яркость" else "КАМЕРА: палец — вращение, два — масштаб"
            label.text=source
            if(::quickLight.isInitialized) skin(quickLight,lightMode)
            if(::gridButton.isInitialized) {
                gridButton.text=if(wireframe) "▦  Сетка: ВКЛ" else "▦  Сетка"
                skin(gridButton,wireframe)
            }
            if(::cameraButton.isInitialized) {
                cameraButton.text=if(lightMode) "☼  Свет: ВКЛ" else "☼  Свет / Камера"
                skin(cameraButton,lightMode)
            }
        }
    }
    private fun showDiagnostic() {
        // The signed UI patch controls section visibility/order and all report labels.
        // Measurements are always taken from actual Android/Filament state.
        fun tr(key:String)=diagnosticLabels[key] ?: key
        fun num(x:Double?)=if (x==null || !x.isFinite()) "—"
            else String.format(Locale("ru","RU"),"%.1f",x)
        val perf=profiler.metrics()
        val ram=try { MemoryMetrics.capture(this) } catch (_:Exception) { null }
        val path="models/${currentId}_${currentLod}${if(wireframe) "_debug" else ""}.glb"
        val mesh=try { GlbMetrics.inspect(updater.readModel(path)) } catch (_:Exception) { null }
        val server=updater.serverPatchVersion.takeIf { it>0 }?.toString() ?: "—"
        val active=updater.installedPatchVersion()
        val confirmed=updater.lastConfirmedVersion()
        val samePatch=server==active.toString() && confirmed==active
        val patchSummary=if(samePatch) "$active ✓ (установлен · кадр · сервер)"
            else "$active / $confirmed / $server ${tr("patchOrder")}"
        val updateMessage=when {
            samePatch && updater.updateStatus.startsWith("Патч $active установлен") ->
                "Актуально, кадр подтверждён"
            samePatch && updater.updateStatus.startsWith("Обновлений нет") ->
                "Актуально"
            else -> updater.updateStatus
        }
        val hasIbl=try {
            assets.open("envs/studio_ibl.ktx").close();true
        } catch (_:Exception) { false }
        val stamp=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale("ru","RU")).format(Date())
        val report=buildString {
            // Version appears ONCE. Patch numbers occupy ONE line.
            appendLine("GlassStudy ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · $stamp")
            for (section in diagnosticSections) {
                when(section) {
                    "updates" -> {
                        appendLine()
                        appendLine("▸ ${tr("sectionUpdates")}")
                        appendLine("${tr("compactPatches")}: $patchSummary")
                        appendLine("${tr("updateCheck")}: $updateMessage")
                    }
                    "performance" -> {
                        appendLine()
                        appendLine("▸ ${tr("sectionPerformance")}")
                        appendLine("FPS: ${num(perf.fps)} · ${tr("frameTime")}: ${num(perf.avgFrameMs)} / ${num(perf.p95FrameMs)} ${tr("milliseconds")} (P95)")
                        appendLine("${tr("cpuProcess")}: ${num(perf.cpuPercent)} % ${tr("oneCore")}")
                        appendLine("${tr("frameSubmission")}: ${num(perf.submissionMs)} ${tr("milliseconds")} (${tr("cpuThread")})")
                        appendLine("${tr("gpuTime")}: ${tr("notMeasured")}")
                    }
                    "memory" -> {
                        appendLine()
                        appendLine("▸ ${tr("sectionMemory")}")
                        if(ram != null) {
                            appendLine("${tr("appRam")}: ${num(ram.pssMb)} ${tr("megabytes")} (PSS)")
                            appendLine("Java / native: ${num(ram.javaHeapMb)} / ${num(ram.nativeHeapMb)} ${tr("megabytes")}")
                            appendLine("${tr("deviceRam")}: ${num(ram.freeDeviceMb)} / ${num(ram.totalDeviceMb)} ${tr("megabytes")} (${tr("freeTotal")})")
                        } else appendLine(tr("notMeasured"))
                    }
                    "scene" -> {
                        appendLine()
                        appendLine("▸ ${tr("sectionScene")}")
                        appendLine("${tr("model")}: ${names[ids.indexOf(currentId).coerceAtLeast(0)]} · $currentLod · ${tr("gridMode")}: ${if(wireframe) tr("yes") else tr("no")}")
                        if(mesh != null) {
                            appendLine("GLB: ${mesh.vertices} ${tr("vertices")} · ${mesh.triangles} ${tr("triangles")}${if(mesh.segments>0) " · ${mesh.segments} ${tr("segments")}" else ""}")
                            appendLine("${mesh.primitives} ${tr("primitives")} · ${num(mesh.bytes/1024.0)} ${tr("kilobytes")} · SHA256: ${mesh.sha12}…")
                        } else appendLine("${tr("geometry")}: ${tr("notMeasured")}")
                    }
                    "renderer" -> {
                        appendLine()
                        appendLine("▸ ${tr("sectionRenderer")}")
                        appendLine("Google Filament / gltfio · OpenGL ES")
                        appendLine("Реально нарисованных кадров Filament: ${viewer.renderedFrames}")
                        appendLine("${tr("glass")}: ${tr("glassValue")}")
                        appendLine("IBL: ${tr(if(hasIbl) "iblAvailable" else "iblUnavailable")}")
                        appendLine("${tr("pathTracing")}: ${tr("pathTracingValue")}")
                    }
                    "camera" -> {
                        appendLine()
                        appendLine("▸ ${tr("sectionCamera")}")
                        appendLine("${tr("distance")}: ${num(distance)} · ${tr("yaw")}: ${num(Math.toDegrees(yaw))}° · ${tr("pitch")}: ${num(Math.toDegrees(pitch))}°")
                        appendLine("${tr("lightAngles")}: ${num(Math.toDegrees(lightAz))}° / ${num(Math.toDegrees(lightElevation))}° · ×${num(brightness)}")
                    }
                    "device" -> {
                        appendLine()
                        appendLine("▸ ${tr("sectionDevice")}")
                        appendLine("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}")
                        appendLine("${tr("screen")}: ${surface.width} × ${surface.height} · DPR ${num(resources.displayMetrics.density.toDouble())}")
                        appendLine("${tr("gpuInfo")}: ${tr("notMeasured")}")
                    }
                }
            }
            appendLine()
            append(tr("measurementNote"))
        }
        AlertDialog.Builder(this).setTitle(tr("title"))
            .setMessage(report).setPositiveButton("ОК",null)
            .setNeutralButton("Копировать") { _, _ ->
                val cb=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("Диагностика GlassStudy",report))
            }.show()
    }
    private fun exportScene() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "GlassStudy_${currentId}_${currentLod}.glassscene")
        }
        startActivityForResult(intent, EXPORT_SCENE)
    }
    @Deprecated("Legacy result callback for portable Android 8+ build")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode!=EXPORT_SCENE || resultCode!=RESULT_OK || data?.data==null) return
        try {
            val scene=org.json.JSONObject()
                .put("version",1)
                .put("renderer","filament_native")
                .put("model","scene.glb")
                .put("object",currentId).put("quality",currentLod)
                .put("camera",org.json.JSONObject().put("yaw",yaw).put("pitch",pitch).put("distance",distance).put("target",org.json.JSONArray(listOf(0,.95,0))))
                .put("lighting",org.json.JSONObject().put("azimuth",lightAz).put("elevation",lightElevation).put("intensity",brightness))
                .put("ior",1.52)
            contentResolver.openOutputStream(data.data!!)?.use { stream ->
                ZipOutputStream(stream).use { zip ->
                    zip.putNextEntry(ZipEntry("scene.glb"))
                    zip.write(updater.readModel("models/${currentId}_${currentLod}${if(wireframe) "_debug" else ""}.glb"))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("scene.json"))
                    zip.write(scene.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            } ?: error("Cannot write to destination")
            Toast.makeText(this,"Сцена сохранена",Toast.LENGTH_LONG).show()
        } catch (e:Exception) {
            AlertDialog.Builder(this).setTitle("Не удалось сохранить")
                .setMessage(e.stackTraceToString().take(2200)).setPositiveButton("OK",null).show()
        }
    }
    override fun onResume() {
        super.onResume()
        if (::viewer.isInitialized && !running) {
            running = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

    }
    override fun onPause() { running=false;profiler.resetAfterPause();Choreographer.getInstance().removeFrameCallback(frameCallback); super.onPause() }
    override fun onDestroy() { running=false;Choreographer.getInstance().removeFrameCallback(frameCallback); super.onDestroy() }
}