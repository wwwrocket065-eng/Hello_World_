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
import android.widget.PopupWindow
import android.widget.ScrollView
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import com.google.android.filament.Camera
import com.google.android.filament.Skybox
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.GlassStudyModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
        private val ids = arrayOf("faceted", "tumbler", "wine", "mug", "pitcher", "bowl", "vase", "sphere", "cube", "cylinder", "cone")
        private val names = arrayOf("Гранёный стакан", "Стакан", "Бокал", "Чашка", "Кувшин", "Пиала", "Ваза", "Сфера", "Куб", "Цилиндр", "Конус")
        private val qualities = arrayOf("low", "medium", "high")
    }
    private lateinit var surface: SurfaceView
    private lateinit var viewer: GlassStudyModelViewer
    private lateinit var updater: PatchManager
    private var wireframe = false
    private lateinit var label: TextView
    private lateinit var logoMark: LabLogoView
    private var diagnosticPopup:PopupWindow?=null
    private lateinit var logoFrame:android.widget.FrameLayout
    private lateinit var topPanel: LinearLayout
    private lateinit var bottomPanel: LinearLayout
    private lateinit var selectorRow: LinearLayout
    private lateinit var topButtonHost: LinearLayout
    private lateinit var bottomButtonHost: LinearLayout
    private val activeButtons=linkedMapOf<String,View>()
    private var activeButtonSpecs:List<UiButtonSpec> = emptyList()
    private var currentUiColor:Int=Color.rgb(27,56,75)
    private var currentSelectorColor=Color.rgb(109,224,222)
    private var currentTopOutlineColor=Color.rgb(109,185,203)
    private lateinit var mainRoot:FrameLayout
    private lateinit var headerSubtitle: TextView
    private lateinit var headerRow: LinearLayout
    private lateinit var fpsPill: TextView
    private var zoomPower = .50
    // Signed .pupdate switch; enabled by default to reverse horizontal object turn.
    private var invertHorizontal = true
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
            intArrayOf(color, Color.argb(Color.alpha(color),16,39,58))).apply {
            cornerRadius=d(radius).toFloat()
            setStroke(d(stroke).coerceAtLeast(1), outline)
        }
    }
    private fun skin(view:View,selected:Boolean=false,color:Int=currentUiColor,outline:Int?=null) {
        val alpha=Color.alpha(color)
        val accent=if(selected) Color.argb(alpha,48,116,124) else color
        val stroke=outline ?: if(selected)Color.rgb(166,246,233) else Color.argb(180,109,185,203)
        val gradient=roundedFill(accent,14f,stroke)
        view.background=RippleDrawable(ColorStateList.valueOf(Color.argb(115,93,230,222)),gradient,null)
        view.elevation=d(2f).toFloat()
    }
    private fun clickAction(action:String) {
        if(action=="diagnostics") {
            if(diagnosticPopup?.isShowing==true) {
                diagnosticPopup?.dismiss()
            } else showDiagnostic()
            return
        }
        diagnosticPopup?.dismiss()
        when(action) {
            "toggleLight" -> { lightMode=!lightMode;status() }
            "resetCamera" -> resetCameraToHome()
            "menu" -> openMenu()
            "selectModel" -> chooseObject.performClick()
            "selectQuality" -> chooseLod.performClick()
            "toggleGrid" -> {
                wireframe=!wireframe
                getPreferences(MODE_PRIVATE).edit().putBoolean("wireframe",wireframe).apply()
                loadSelected()
                status()
            }
            "toggleFps" -> {
                fpsPill.visibility=if(fpsPill.visibility==View.VISIBLE) View.GONE else View.VISIBLE
            }
            else -> throw IllegalArgumentException("Unknown UI action")
        }
    }
    private fun isActionActive(action:String)=when(action) {
        "toggleLight" -> lightMode
        "toggleGrid" -> wireframe
        else -> false
    }
    private fun updateButtonHighlights() {
        activeButtonSpecs.forEach { spec ->
            activeButtons[spec.id]?.let {
                skin(it,spec.highlight || isActionActive(spec.action),
                    spec.fill?:currentUiColor,
                    if(spec.slot=="top")currentTopOutlineColor else spec.outline)
            }
        }
    }
    private fun createUiButton(spec:UiButtonSpec,ui:UiLayout):View {
        val ink=spec.textColor?:ui.buttonTextColor
        val target=if(spec.slot=="top") {
            LabIconView(this,spec.icon).apply {
                iconColor=ink
                glyphScale=ui.topIconScale
                chipCenterFilled=ui.fillChipCenter
                contentDescription=when(spec.action) {
                    "diagnostics" -> "Диагностика"
                    "toggleLight" -> "Свет / Камера"
                    "resetCamera" -> "Сброс камеры"
                    "menu" -> "Меню"
                    else -> if(spec.label.isBlank()) spec.action else spec.label
                }
            }
        } else {
            LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER
                val icon=LabIconView(this@MainActivity,spec.icon).apply {
                    iconColor=ink
                    glyphScale=.80f
                    chipCenterFilled=ui.fillChipCenter
                }
                addView(icon,LinearLayout.LayoutParams(d(25f),d(25f)))
                val text=TextView(this@MainActivity).apply {
                    this.text=spec.label
                    textSize=if(spec.label.length>22) 12f else 14f
                    setTextColor(ink)
                    gravity=Gravity.CENTER
                    isSingleLine=true
                    setPadding(d(5f),0,0,0)
                }
                addView(text)
                contentDescription=spec.label
            }
        }
        target.isClickable=true
        target.isFocusable=true
        target.setOnClickListener { clickAction(spec.action) }
        if(spec.action=="toggleLight") {
            target.setOnLongClickListener {
                resetCameraToHome()
                Toast.makeText(this,"Камера сброшена",Toast.LENGTH_SHORT).show()
                true
            }
        }
        skin(target,spec.highlight || isActionActive(spec.action),spec.fill?:ui.buttonColor,
            if(spec.slot=="top")ui.topButtonOutlineColor else spec.outline)
        return target
    }
    private fun renderUiButtons(ui:UiLayout) {
        // Spec was validated BEFORE rebuilding. No code, arbitrary URLs or view
        // class names can come from a .pupdate: only whitelisted actions/icons.
        topButtonHost.removeAllViews()
        bottomButtonHost.removeAllViews()
        activeButtons.clear()
        activeButtonSpecs=ui.buttons
        val top=ui.buttons.filter{it.slot=="top"}
        val bottom=ui.buttons.filter{it.slot=="bottom"}
        val totalDp=resources.displayMetrics.widthPixels/resources.displayMetrics.density
        val upperBound=(if(top.isEmpty())46f else ((totalDp-186f)/top.size)-4f)
        val size=ui.topButtonSizeDp.toFloat().coerceAtMost(upperBound).coerceAtLeast(28f)
        for(spec in top) {
            val v=createUiButton(spec,ui)
            val p=LinearLayout.LayoutParams(d(size),d(size))
            p.leftMargin=d(4f)
            topButtonHost.addView(v,p)
            activeButtons[spec.id]=v
        }
        for(group in bottom.chunked(2)) {
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            for((index,spec) in group.withIndex()) {
                val v=createUiButton(spec,ui)
                val p=LinearLayout.LayoutParams(0,d(spec.heightDp.toFloat()),1f)
                if(index>0)p.leftMargin=d(8f)
                row.addView(v,p)
                activeButtons[spec.id]=v
            }
            val rowParams=LinearLayout.LayoutParams(-1,-2)
            rowParams.topMargin=d(8f)
            bottomButtonHost.addView(row,rowParams)
        }
    }
    private fun selectionAdapter(values:Array<String>):ArrayAdapter<String> {
        val adapter=object: ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values) {
            override fun getView(position:Int, convertView:View?, parent:ViewGroup):View {
                val label=super.getView(position,convertView,parent) as TextView
                label.text="${values[position]}  ▾"
                label.textSize=14f
                label.setSingleLine(true)
                label.setTextColor(currentSelectorColor)
                label.setPadding(d(12f),0,d(7f),0)
                return label
            }
            override fun getDropDownView(position:Int, convertView:View?,parent:ViewGroup):View {
                val label=super.getDropDownView(position,convertView,parent) as TextView
                label.text=values[position]
                label.textSize=16f
                label.setTextColor(currentSelectorColor)
                label.setPadding(d(15f),d(10f),d(9f),d(10f))
                label.setBackgroundColor(Color.rgb(24,48,62))
                return label
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }
    private fun openMenu() {
        diagnosticPopup?.dismiss()
        AlertDialog.Builder(this).setTitle("GlassStudy · 3D LAB")
            .setItems(arrayOf("Выбрать объект", "Сбросить камеру", "Диагностика")) { _,which ->
                when(which) {
                    0 -> chooseObject.performClick()
                    1 -> resetCameraToHome()
                    2 -> showDiagnostic()
                }
            }.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updater = PatchManager(this)
        updater.reconcileAfterCrash()
        wireframe = getPreferences(MODE_PRIVATE).getBoolean("wireframe", false)
        window.statusBarColor = Color.rgb(101,103,103)
        window.navigationBarColor = Color.rgb(101,103,103)
        surface = SurfaceView(this)
        val root = FrameLayout(this)
        mainRoot=root
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
        // Actual logo geometry, including the requested longer underline.
        logoMark=LabLogoView(this)
        logoFrame=FrameLayout(this)
        val logoParams=FrameLayout.LayoutParams(d(31f),d(38f),Gravity.CENTER)
        logoFrame.addView(logoMark,logoParams)
        headerRow.addView(logoFrame,LinearLayout.LayoutParams(d(33f),d(48f)))
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
        topButtonHost=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        headerRow.addView(topButtonHost,LinearLayout.LayoutParams(-2,d(48f)))
        topPanel.addView(headerRow)
        label=TextView(this).apply {
            textSize=11f
            gravity=Gravity.CENTER
            setTextColor(Color.rgb(220,238,242))
            visibility=View.GONE
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
            setTextColor(Color.rgb(109,224,222))
            setPadding(d(13f),d(6f),d(13f),d(6f))
            background=roundedFill(Color.rgb(19,41,54),30f,Color.argb(130,109,185,203))
        }
        val pillParams=LinearLayout.LayoutParams(-2,-2)
        pillParams.bottomMargin=d(6f)
        dock.addView(fpsPill,pillParams)

        bottomPanel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(d(11f),d(11f),d(11f),d(12f))
            background=roundedFill(Color.argb(220,35,47,58),19f,Color.argb(175,177,218,225))
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

        bottomButtonHost=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
        }
        bottomPanel.addView(bottomButtonHost,LinearLayout.LayoutParams(-1,-2))
        // No fixed Apply / Export / Diagnostics buttons below. All actions
        // are created by the signed declarative UI patch layout.
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

    /** System bar appearance and immersive mode are signed patch settings. */
    private fun setSystemBarMode(ui:UiLayout) {
        window.statusBarColor=ui.systemBarColor
        window.navigationBarColor=ui.systemBarColor
        if(Build.VERSION.SDK_INT>=30) {
            val controller=window.insetsController
            if(ui.immersiveMode) {
                controller?.hide(WindowInsets.Type.systemBars())
                controller?.systemBarsBehavior=
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else controller?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility=if(ui.immersiveMode) (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) else 0
        }
        mainRoot.requestApplyInsets()
    }

    /** Safe dynamic layout: all add/remove/move operations are signed .pupdate JSON. */
    private fun applyUiLayout(candidate:PatchManager.Candidate?=null):Boolean {
        try {
            val ui=UiLayout.parse(updater.readUiConfig(candidate))
            // The signed JSON is fully validated first: no partial invalid actions.
            zoomPower=ui.zoomPower
            invertHorizontal=ui.invertHorizontal
            currentSelectorColor=ui.selectorTextColor
            currentTopOutlineColor=ui.topButtonOutlineColor
            logoMark.showUnderline=ui.showLogoUnderline
            logoMark.logoColor=ui.logoColor
            val logoParams=logoMark.layoutParams as FrameLayout.LayoutParams
            logoParams.width=d(ui.logoSizeDp.toFloat())
            logoParams.height=d(ui.logoSizeDp*1.22f)
            logoParams.topMargin=d(ui.logoOffsetYDp.toFloat())
            logoParams.gravity=Gravity.CENTER
            logoMark.layoutParams=logoParams
            fpsPill.setTextColor(ui.fpsTextColor)
            label.visibility=if(ui.showCameraHint)View.VISIBLE else View.GONE
            (chooseObject.adapter as? android.widget.BaseAdapter)?.notifyDataSetChanged()
            (chooseLod.adapter as? android.widget.BaseAdapter)?.notifyDataSetChanged()
            diagnosticLabels=ui.diagnostics
            diagnosticSections=ui.diagnosticSections
            currentUiColor=ui.buttonColor
            label.textSize=ui.textSizeSp.coerceIn(11f,14f)
            label.setTextColor(ui.textColor)
            fpsPill.visibility=if(ui.showFps)View.VISIBLE else View.GONE
            val opacity=ui.panelOpacity
            // Top shading independently configurable; zero removes the dark veil
            // without changing the bottom panel opacity.
            topPanel.background=if(ui.topOverlayOpacity==0) null else
                GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.argb(ui.topOverlayOpacity,24,34,44),
                        Color.argb(0,24,34,44)))
            bottomPanel.background=roundedFill(Color.argb(opacity,35,47,58),19f,
                Color.argb(180,177,218,225))
            renderUiButtons(ui)
            setSystemBarMode(ui)
            status()
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
                    // A signed UI patch can flip direction; default is inverted.
                    yaw += dx * (if(invertHorizontal) -.008 else .008)
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
            updateButtonHighlights()
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
        // A non-modal anchored overlay rather than AlertDialog. Outside taps
        // dismiss it; top toolbar buttons remain clickable and switch actions.
        val density=resources.displayMetrics.density
        val margin=d(18f)
        val availableWidth=(resources.displayMetrics.widthPixels-margin*2).coerceAtLeast(d(220f))
        val outer=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(d(15f),d(14f),d(15f),d(14f))
            background=roundedFill(Color.argb(225,76,80,87),19f,
                Color.argb(180,161,190,205))
        }
        val header=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val heading=TextView(this).apply {
            text="Диагностика"
            textSize=15f
            setTextColor(Color.rgb(219,235,239))
            gravity=Gravity.CENTER_VERTICAL
        }
        header.addView(heading,LinearLayout.LayoutParams(0,d(40f),1f))
        // The only actionable item in the header is the framed copy glyph.
        val copy=FrameLayout(this).apply {
            contentDescription="Копировать диагностику"
            isFocusable=true
            isClickable=true
            background=roundedFill(Color.argb(215,16,44,60),8f,
                Color.rgb(91,185,193))
        }
        val copyIcon=LabIconView(this,"copy").apply {
            iconColor=Color.rgb(109,224,222)
        }
        copy.addView(copyIcon,FrameLayout.LayoutParams(d(31f),d(31f),Gravity.CENTER))
        header.addView(copy,LinearLayout.LayoutParams(d(43f),d(40f)))
        outer.addView(header)
        val reportText=TextView(this).apply {
            text=report
            setTextColor(Color.rgb(175,212,225))
            typeface=Typeface.MONOSPACE
            textSize=11.5f
            setTextIsSelectable(true)
            setPadding(d(10f),d(12f),d(10f),d(10f))
        }
        val scrolling=ScrollView(this).apply {
            isFillViewport=false
            background=roundedFill(Color.rgb(5,23,36),8f,
                Color.argb(100,100,155,165))
            addView(reportText)
        }
        val heightCap=(resources.displayMetrics.heightPixels*.63).toInt()
        val contentParams=LinearLayout.LayoutParams(-1,heightCap)
        contentParams.topMargin=d(10f)
        outer.addView(scrolling,contentParams)
        val popup=PopupWindow(outer,availableWidth,-2,false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable=true
            isTouchable=true
            isClippingEnabled=true
            if(Build.VERSION.SDK_INT>=29) setTouchModal(false)
            elevation=d(10f).toFloat()
            setOnDismissListener {
                if(diagnosticPopup===this)diagnosticPopup=null
            }
        }
        copy.setOnClickListener {
            val cb=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText("Диагностика GlassStudy",report))
            Toast.makeText(this,"Скопировано",Toast.LENGTH_SHORT).show()
        }
        diagnosticPopup=popup
        // Lower than toolbar, so another toolbar icon remains accessible.
        popup.showAtLocation(mainRoot,Gravity.TOP or Gravity.CENTER_HORIZONTAL,0,
            d(69f))
    }
    }
    override fun onResume() {
        super.onResume()
        if (::viewer.isInitialized && !running) {
            running = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

    }
    @Deprecated("Legacy Android back callback; closes only the open report")
    override fun onBackPressed() {
        if(diagnosticPopup?.isShowing==true) diagnosticPopup?.dismiss()
        else super.onBackPressed()
    }
    override fun onPause() { diagnosticPopup?.dismiss();running=false;profiler.resetAfterPause();Choreographer.getInstance().removeFrameCallback(frameCallback); super.onPause() }
    override fun onDestroy() { running=false;Choreographer.getInstance().removeFrameCallback(frameCallback); super.onDestroy() }
}