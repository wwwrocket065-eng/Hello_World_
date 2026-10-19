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
import android.content.res.Configuration
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
    private val diagnosticsPopup by lazy(LazyThreadSafetyMode.NONE) {
        DiagnosticsPopupController(this,mainRoot) { updateButtonHighlights() }
    }
    private val diagnosticsReports by lazy(LazyThreadSafetyMode.NONE) {
        DiagnosticsReportService(this,updater,profiler)
    }
    private lateinit var logoFrame:android.widget.FrameLayout
    private lateinit var topPanel: LinearLayout
    private lateinit var bottomPanel: LinearLayout
    private lateinit var selectorRow: LinearLayout
    private lateinit var topButtonHost: LinearLayout
    private lateinit var bottomButtonHost: LinearLayout
    private lateinit var toolbarController:UiToolbarController
    private var patchWindows: Map<String, DeclarativeWindow> = emptyMap()

    /** All controls (toolbar, windows, overlays) go through one typed action API. */
    private val uiController by lazy(LazyThreadSafetyMode.NONE) {
        UiInteractionController(object : UiInteractionController.Host {
            override fun isDiagnosticsOpen() = diagnosticsPopup.isShowing
            override fun diagnosticsLastDismissedAtMs() = diagnosticsPopup.dismissedAtMs
            override fun closeDiagnostics() { diagnosticsPopup.dismiss() }
            override fun showDiagnostics() { showDiagnostic() }
            override fun isWindowOpen(key: String) = GlassDialogFactory.isOpen(key)
            override fun closeWindow() { GlassDialogFactory.closeCurrent() }
            override fun openPatchWindow(id: String) {
                this@MainActivity.openPatchWindow(id)
            }
            override fun windowMode(key: String): String =
                GlassDialogFactory.theme.windowClickModes[key] ?: "toggle"

            override fun performNativeAction(action: String) {
                when (action) {
                    "resetCamera" -> resetCameraToHome()
                    "openScene" -> openSceneMenu()
                    "menu" -> openMenu()
                    "selectModel" -> chooseObject.performClick()
                    "selectQuality" -> chooseLod.performClick()
                    else -> throw IllegalArgumentException("Unknown native action: $action")
                }
            }

            override fun readState(binding: String): Float = when (binding) {
                "lightMode" -> if (lightMode) 1f else 0f
                "grid" -> if (wireframe) 1f else 0f
                "fps" -> if (fpsPill.visibility == View.VISIBLE) 1f else 0f
                "brightnessPercent" -> (brightness * 100.0).toFloat()
                else -> throw IllegalArgumentException("Unsupported binding: $binding")
            }

            override fun writeState(binding: String, value: Float) {
                when (binding) {
                    "lightMode" -> {
                        lightMode = value >= .5f
                        lightGesture.reset()
                        if(!lightMode)lightIndicator?.hide()
                        status()
                    }
                    "grid" -> {
                        val enabled = value >= .5f
                        if (enabled != wireframe) {
                            wireframe = enabled
                            getPreferences(MODE_PRIVATE).edit()
                                .putBoolean("wireframe", wireframe).apply()
                            loadSelected()
                            status()
                        }
                    }
                    "fps" -> {
                        fpsPill.visibility = if (value >= .5f) View.VISIBLE else View.GONE
                        updateButtonHighlights()
                    }
                    "brightnessPercent" -> {
                        brightness = (value.toDouble() / 100.0)
                            .coerceIn(0.0, 2.0)
                        updateCamera()
                    }
                    else -> throw IllegalArgumentException("Unsupported binding: $binding")
                }
            }
        })
    }

    private var uiAnimations: Map<String, UiAnimationSpec> = emptyMap()
    private var currentUiColor:Int=Color.rgb(27,56,75)
    private var currentSelectorColor=Color.rgb(109,224,222)
    private var currentTopOutlineColor=Color.rgb(109,185,203)
    private var currentTopIconColor=Color.rgb(163,208,211)
    private var currentTopButtonSizeDp=35
    private var currentTopIconScale=.82f
    private lateinit var mainRoot:FrameLayout
    private lateinit var headerSubtitle: TextView
    private lateinit var headerRow: LinearLayout
    private lateinit var fpsPill: TextView
    private var cameraModule = CameraModuleConfig()
    private var renderModule = RenderModuleConfig()
    private var diagnosticsModule = DiagnosticsModuleConfig()
    private var materialModule = MaterialModuleConfig()
    private lateinit var sceneEnvironment:SceneEnvironmentController
    private lateinit var sceneSettings:SceneSettingsPanel
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
    private var overlayModule=OverlayModuleConfig()
    private var lightIndicator:LightIndicatorView?=null
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
    private val lightGesture = LightGestureController()
    private var running = false
    private var stableFrames = 0
    private var localPatchBusy = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            Choreographer.getInstance().postFrameCallback(this)
            val submitStartNs=SystemClock.elapsedRealtimeNanos()
            updateCamera()
            val framesBefore = viewer.renderedFrames
            viewer.render(frameTimeNanos)
            val frameWasRendered = viewer.renderedFrames > framesBefore
            val submissionMs=(SystemClock.elapsedRealtimeNanos()-submitStartNs)/1_000_000.0
            profiler.frame(frameTimeNanos,submissionMs)
            if (::fpsPill.isInitialized && viewer.renderedFrames % 40L == 0L) {
                val f=profiler.metrics().fps
                fpsPill.text="${if(f!=null) String.format(Locale.US,"%.0f",f) else "—"} FPS · Filament"
            }
            if (frameWasRendered && ++stableFrames == 4) updater.confirmFrameRendered()
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
    /**
     * All patch-defined windows, including their interactive controls, use one
     * renderer and a fixed whitelist of safe application state bindings.
     */
    private fun openPatchWindow(id: String) {
        val spec = patchWindows[id] ?: return
        PatchWindowRenderer.show(
            this, spec, uiAnimations[id],
            readBinding = uiController::read,
            writeBinding = uiController::write,
            performAction = uiController::dispatch
        )
    }

    private fun updateButtonHighlights() {
        if(::toolbarController.isInitialized)toolbarController.updateHighlights()
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
    private fun openSceneMenu() {
        sceneSettings.show()
    }

    private fun openMenu() {
        diagnosticsPopup.dismiss()
        // Dedicated toolbar actions never appear a second time in settings.
        GlassDialogFactory.menu(this,GlassDialogFactory.theme.titles.getValue("mainMenu"),
            listOf("Стиль индикаторов освещения")) { showLightingIndicatorStyles() }
    }
    private fun showLightingIndicatorStyles() {
        LightIndicatorSettingsPanel(this) {
            updateContextualOverlay()
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
        sceneEnvironment=SceneEnvironmentController(this,viewer,getPreferences(MODE_PRIVATE))
        sceneSettings=SceneSettingsPanel(this,sceneEnvironment)
        sceneEnvironment.applySkybox()
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
        applyRenderModule()
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
            setTextColor(Color.rgb(158,220,225))
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
        // Non-interactive HUD above the Filament SurfaceView.
        lightIndicator=LightIndicatorView(this).also { hud ->
            root.addView(hud,FrameLayout.LayoutParams(d(overlayModule.indicatorSizeDp.toFloat()),
                d(overlayModule.indicatorSizeDp.toFloat()),Gravity.CENTER))
        }

        toolbarController=UiToolbarController(this,topButtonHost,bottomButtonHost,topPanel,
            object:UiToolbarController.Host {
                override fun dispatch(action:String) { uiController.dispatch(action) }
                override fun isActive(action:String):Boolean=when(action) {
                    "toggleLight" -> lightMode
                    "toggleGrid" -> wireframe
                    "diagnostics" -> diagnosticsPopup.isShowing
                    else -> false
                }
                override fun decorate(view:View,selected:Boolean,color:Int,outline:Int?) {
                    skin(view,selected,color,outline)
                }
                override fun onLightLongPress() {
                    resetCameraToHome()
                    Toast.makeText(this@MainActivity,"Камера сброшена",Toast.LENGTH_SHORT).show()
                }
            })
        chooseObject.setSelection(1)
        chooseLod.setSelection(1)
        applyUiLayout()
        loadSelected()
        sceneEnvironment.applyFloor()
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

    /** Handle rotation without destroying Filament, camera state, or loaded models. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Remember the active panel before Android changes its viewport. A
        // rotation must not silently throw away a window or reset the scene.
        val patchToRestore = patchWindows.keys.firstOrNull {
            GlassDialogFactory.isOpen("patch:$it")
        }
        val restoreScene = GlassDialogFactory.isOpen("openScene")
        val restoreMenu = GlassDialogFactory.isOpen("menu")
        val restoreDiagnostics = diagnosticsPopup.isShowing
        diagnosticsPopup.dismiss()
        GlassDialogFactory.closeCurrent()
        if (::mainRoot.isInitialized) {
            mainRoot.post {
                if (!isFinishing && !isDestroyed && ::updater.isInitialized) {
                    if (applyUiLayout()) {
                        updateContextualOverlay()
                        when {
                            patchToRestore != null -> openPatchWindow(patchToRestore)
                            restoreScene -> openSceneMenu()
                            restoreMenu -> openMenu()
                            restoreDiagnostics -> showDiagnostic()
                        }
                    }
                }
            }
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
                    GlassDialogFactory.message(this,GlassDialogFactory.theme.titles.getValue("patchError"),reason)
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
                GlassDialogFactory.message(this,GlassDialogFactory.theme.titles.getValue("patchInstalled"),message)
            } else {
                Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
            }
        } else {
            updater.reject(candidate)
            applyUiLayout()
            loadSelected()
            if (fromDownloads) {
                GlassDialogFactory.message(this,GlassDialogFactory.theme.titles.getValue("patchRejected"),
                    "Модель или настройки не прошли проверку. Рабочая версия сохранена.")
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

    private fun applyRenderModule() {
        if (!::viewer.isInitialized) return
        viewer.camera.setExposure(renderModule.exposureAperture, renderModule.exposureShutter,
            renderModule.exposureSensitivity)
        viewer.view.antiAliasing = if (renderModule.fxaa)
            com.google.android.filament.View.AntiAliasing.FXAA
            else com.google.android.filament.View.AntiAliasing.NONE
        viewer.scene.indirectLight?.intensity = renderModule.iblIntensity
    }

    /** Safe dynamic layout: all add/remove/move operations are signed .pupdate JSON. */
    private fun applyUiLayout(candidate:PatchManager.Candidate?=null):Boolean {
        try {
            val bundle = PatchUiBundle.read(updater, candidate)
            val ui = bundle.ui
            // Parsing every module completed before any live state is replaced.
            patchWindows = ui.windows
            uiAnimations = ui.animations
            cameraModule = bundle.camera
            renderModule = bundle.render
            diagnosticsModule = bundle.diagnostics
            materialModule = bundle.materials
            sceneEnvironment.applyConfiguration(bundle.scene)
            GlassDialogFactory.applyTheme(bundle.windows)
            overlayModule = bundle.overlay
            updateContextualOverlay()
            applyRenderModule()
            zoomPower=ui.zoomPower
            invertHorizontal=ui.invertHorizontal
            currentSelectorColor=ui.selectorTextColor
            currentTopOutlineColor=ui.topButtonOutlineColor
            currentTopIconColor=ui.buttonTextColor
            currentTopButtonSizeDp=ui.topButtonSizeDp
            currentTopIconScale=ui.topIconScale
            logoMark.showUnderline=ui.showLogoUnderline
            logoMark.logoColor=ui.logoColor
            headerSubtitle.setTextColor(ui.subtitleColor)
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
            toolbarController.render(ui,uiAnimations)
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
            MaterialOverrides.parse(updater.readModuleConfig("materials", candidate)).apply(viewer)
            status()
            return true
        } catch (e: Exception) {
            if (candidate != null) {
                // Reject bad background patches silently: preserve the working scene.
                android.util.Log.w("GlassStudy", "Staged model failed to render", e)
            } else {
                GlassDialogFactory.message(this,GlassDialogFactory.theme.titles.getValue("modelError"),
                    e.stackTraceToString().take(3000))
            }
            return false
        }
    }
    private fun modelData(candidate: PatchManager.Candidate? = null): ByteBuffer {
        val defaultName="models/${currentId}_${currentLod}${if(wireframe) "_debug" else ""}.glb"
        val catalog = if (candidate != null)
            MaterialModuleConfig.parse(updater.readModuleConfig("materials", candidate))
            else materialModule
        val name = catalog.resolve(currentId, currentLod, wireframe) ?: defaultName
        val bytes = updater.readResourceOrNull(name, candidate)
            ?: throw IllegalArgumentException("Missing material/model asset: $name")
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); flip() }
    }
    private fun resetCameraToHome() {
        yaw = .56
        pitch = .34
        distance = 5.5
        lightMode = false
        lightGesture.reset()
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
        viewer.camera.setProjection(cameraModule.fieldOfView, aspect, cameraModule.nearClip, cameraModule.farClip, Camera.Fov.VERTICAL)
        val dx = cos(lightElevation) * sin(lightAz)
        val dy = sin(lightElevation)
        val dz = cos(lightElevation) * cos(lightAz)
        val mgr = viewer.engine.lightManager
        val inst = mgr.getInstance(viewer.light)
        mgr.setDirection(inst, (-dx).toFloat(),(-dy).toFloat(),(-dz).toFloat())
        mgr.setIntensity(inst,(cameraModule.lightIntensity * brightness).toFloat())
    }
    private fun showLightIndicator(mode:LightIndicatorView.Mode,number:Float) {
        if(!lightMode || !overlayModule.enabled || overlayModule.showWith=="never")return
        lightIndicator?.show(mode,number,LightingIndicatorStyles.selected(this,mode),
            LightingIndicatorStyles.color(this,mode,overlayModule.indicatorColor(mode)),
            overlayModule.guideColor)
    }
    private fun touch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                lightGesture.reset()
                oldX=e.getX(0); oldY=e.getY(0)
                oldPinch=if (e.pointerCount>=2) pinch(e) else 0f
                if(lightMode && e.pointerCount>=2)
                    showLightIndicator(LightIndicatorView.Mode.BRIGHTNESS,(brightness*100).toFloat())
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // The surviving finger begins a fresh axis decision.
                lightGesture.reset()
                lightIndicator?.hide()
                val remaining=if (e.actionIndex==0 && e.pointerCount>1) 1 else 0
                oldX=e.getX(remaining); oldY=e.getY(remaining)
                oldPinch=0f
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lightGesture.reset()
                lightIndicator?.hide()
                oldPinch=0f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val density=resources.displayMetrics.density.coerceAtLeast(.1f)
                if (e.pointerCount>=2) {
                    lightGesture.reset()
                    val span=pinch(e)
                    if(oldPinch>0f && span>1f) {
                        if(lightMode) {
                            // Additive pinch can brighten again even from 0%.
                            val next=(brightness+(span-oldPinch)/density/160.0).coerceIn(0.0,2.0)
                            uiController.write("brightnessPercent",(next*100.0).toFloat())
                            showLightIndicator(LightIndicatorView.Mode.BRIGHTNESS,(brightness*100).toFloat())
                        } else {
                            distance=(distance*(oldPinch/span).toDouble().pow(zoomPower))
                                .coerceIn(cameraModule.minDistance,cameraModule.maxDistance)
                        }
                    }
                    oldPinch=span
                } else {
                    // Gesture sensitivity is defined per dp, not physical pixels.
                    val dx=(e.x-oldX).toDouble()/density
                    val dy=(e.y-oldY).toDouble()/density
                    if(lightMode) {
                        val motion=lightGesture.move(dx,dy)
                        if(motion!=null) when(motion.axis) {
                            LightGestureController.Axis.AZIMUTH -> {
                                val turn=2.0*Math.PI
                                lightAz=(lightAz+motion.deltaDp*cameraModule.lightYawSensitivity)%turn
                                if(lightAz<0.0) lightAz+=turn
                                showLightIndicator(LightIndicatorView.Mode.AZIMUTH,
                                    Math.toDegrees(lightAz).toFloat())
                            }
                            LightGestureController.Axis.ELEVATION -> {
                                // 180-degree vertical arc: from 0 to 180 degrees.
                                lightElevation=(lightElevation+
                                    motion.deltaDp*cameraModule.lightPitchSensitivity)
                                    .coerceIn(0.0,Math.PI)
                                showLightIndicator(LightIndicatorView.Mode.ELEVATION,
                                    Math.toDegrees(lightElevation).toFloat())
                            }
                        }
                    } else {
                        yaw+=dx*(if(invertHorizontal)-cameraModule.yawSensitivity else cameraModule.yawSensitivity)
                        pitch=(pitch+dy*cameraModule.pitchSensitivity).coerceIn(-.05,1.23)
                    }
                }
                oldX=e.getX(0);oldY=e.getY(0)
                return true
            }
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
            updateContextualOverlay()
        }
    }
    /** Passive indicator replaces the previous interactive brightness slider. */
    private fun updateContextualOverlay() {
        lightIndicator?.let { hud ->
            val px=d(overlayModule.indicatorSizeDp.toFloat())
            val size=hud.layoutParams as FrameLayout.LayoutParams
            size.width=px;size.height=px;hud.layoutParams=size
            if(!lightMode || !overlayModule.enabled || overlayModule.showWith=="never")
                hud.hide()
        }
    }

    private fun showDiagnostic() {
        val report=diagnosticsReports.create(DiagnosticsReportState(
            diagnosticLabels=diagnosticLabels,
            diagnosticSections=diagnosticSections,
            diagnosticsModule=diagnosticsModule,
            renderModule=renderModule,
            cameraModule=cameraModule,
            modelId=currentId,
            modelName=names[ids.indexOf(currentId).coerceAtLeast(0)],
            modelLod=currentLod,
            wireframe=wireframe,
            renderedFrames=viewer.renderedFrames.toString(),
            surfaceWidth=surface.width,
            surfaceHeight=surface.height,
            pixelDensity=resources.displayMetrics.density.toDouble(),
            distance=distance,
            yaw=yaw,
            pitch=pitch,
            lightAz=lightAz,
            lightElevation=lightElevation,
            brightness=brightness
        ))
        diagnosticsPopup.show(report,DiagnosticsPopupStyle(
            fill=currentUiColor,border=currentTopOutlineColor,
            iconColor=currentTopIconColor,iconScale=currentTopIconScale,
            iconSizeDp=currentTopButtonSizeDp
        ))
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
        if(diagnosticsPopup.isShowing) diagnosticsPopup.dismiss()
        else super.onBackPressed()
    }
    override fun onPause() { lightIndicator?.hide();diagnosticsPopup.dismiss();running=false;profiler.resetAfterPause();Choreographer.getInstance().removeFrameCallback(frameCallback); super.onPause() }
    override fun onDestroy() {
        running=false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        if(::sceneEnvironment.isInitialized)sceneEnvironment.release()
        super.onDestroy()
    }
}