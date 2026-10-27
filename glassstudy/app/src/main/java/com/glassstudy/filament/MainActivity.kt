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
import android.widget.SeekBar
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
    companion object { init { Utils.init() } }
    private lateinit var surface: SurfaceView
    private lateinit var viewer: GlassStudyModelViewer
    private lateinit var updater: PatchManager
    private lateinit var modelAssets:ModelMaterialController
    private var initialWireframe=false
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
    private lateinit var qualityRow:LinearLayout
    private lateinit var qualitySlider:SeekBar
    private lateinit var qualityValue:TextView
    private lateinit var adaptiveRow:LinearLayout
    private lateinit var adaptiveSlider:SeekBar
    private lateinit var adaptiveValue:TextView
    private var pendingAdaptiveUpdate:Runnable?=null
    private lateinit var toolbarController:UiToolbarController

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
                    "toggleQualityPanel" -> {
                        qualityRow.visibility=if(qualityRow.visibility==View.VISIBLE) View.GONE else View.VISIBLE
                        updateButtonHighlights()
                    }
                    "toggleAdaptivePanel" -> {
                        adaptiveRow.visibility=if(adaptiveRow.visibility==View.VISIBLE) View.GONE else View.VISIBLE
                        updateButtonHighlights()
                    }
                    else -> throw IllegalArgumentException("Unknown native action: $action")
                }
            }

            override fun readState(binding: String): Float = when (binding) {
                "lightMode" -> if (cameraLight.lightMode) 1f else 0f
                "grid" -> if (modelAssets.wireframe) 1f else 0f
                "fps" -> if (fpsPill.visibility == View.VISIBLE) 1f else 0f
                "fxaa" -> if(renderEnvironment.fxaaEnabled)1f else 0f
                "qualityLevel" -> qualitySlider.progress.toFloat()
                "adaptivityPercent" -> modelAssets.adaptivePercent.toFloat()
                "brightnessPercent" -> (cameraLight.brightness * 100.0).toFloat()
                else -> throw IllegalArgumentException("Unsupported binding: $binding")
            }

            override fun writeState(binding: String, value: Float) {
                when (binding) {
                    "lightMode" -> {
                        cameraLight.setLightMode(value>=.5f)
                        status()
                    }
                    "grid" -> {
                        val enabled=value>=.5f
                        if(enabled!=modelAssets.wireframe) {
                            if(loadSelected(debugOverride=enabled)) {
                                getPreferences(MODE_PRIVATE).edit()
                                    .putBoolean("wireframe",enabled).apply()
                            }
                            status()
                        }
                    }
                    "fps" -> {
                        fpsPill.visibility = if (value >= .5f) View.VISIBLE else View.GONE
                        updateButtonHighlights()
                    }
                    "fxaa" -> {
                        val enabled=value>=.5f
                        renderEnvironment.setFxaaOverride(enabled)
                        getPreferences(MODE_PRIVATE).edit().putBoolean("fxaaOverride",enabled).apply()
                        updateButtonHighlights()
                    }
                    "qualityLevel" -> {
                        val level=value.toInt().coerceIn(0,ModelCatalog.qualities.lastIndex)
                        // The old spinner is GONE and does not reliably
                        // dispatch selection events. Reload the real GLB now,
                        // with the slider as the single source of truth.
                        val requested=ModelCatalog.qualityAt(level)
                        val ok=modelAssets.selection.lod==requested || loadSelected()
                        val actual=if(ok)level else ModelCatalog.indexOfQuality(modelAssets.selection.lod)
                        if(qualitySlider.progress!=actual)qualitySlider.progress=actual
                        chooseLod.setSelection(actual)
                        qualityValue.text="Качество: ${ModelCatalog.qualityNames[actual]}"
                        if(!ok)Toast.makeText(this@MainActivity,
                            "Не удалось изменить качество сетки",Toast.LENGTH_SHORT).show()
                    }
                    "adaptivityPercent" -> {
                        val requested=value.toInt().coerceIn(0,100)
                        val result=modelAssets.setAdaptivity(requested)
                        if(result.isSuccess) {
                            getPreferences(MODE_PRIVATE).edit()
                                .putInt("adaptivityPercent",requested).apply()
                            if(::adaptiveSlider.isInitialized && adaptiveSlider.progress!=requested)
                                adaptiveSlider.progress=requested
                            if(::adaptiveValue.isInitialized)
                                adaptiveValue.text="Адаптивность: $requested%"
                            status()
                        } else {
                            if(::adaptiveSlider.isInitialized)
                                adaptiveSlider.progress=modelAssets.adaptivePercent
                            Toast.makeText(this@MainActivity,"Не удалось изменить сетку",Toast.LENGTH_SHORT).show()
                        }
                    }
                    "brightnessPercent" -> cameraLight.setBrightnessPercent(value)
                    else -> throw IllegalArgumentException("Unsupported binding: $binding")
                }
            }
        })
    }

    private val uiChrome by lazy(LazyThreadSafetyMode.NONE) { UiChromeController(this) }
    private lateinit var mainRoot:FrameLayout
    private lateinit var headerSubtitle: TextView
    private lateinit var headerRow: LinearLayout
    private lateinit var fpsPill: TextView
    private lateinit var cameraLight:CameraLightController
    private lateinit var renderEnvironment:RenderEnvironmentController
    private var diagnosticsModule = DiagnosticsModuleConfig()
    private lateinit var sceneEnvironment:SceneEnvironmentController
    private lateinit var sceneSettings:SceneSettingsPanel
    private val profiler = FrameProfiler()
    private lateinit var chooseObject: Spinner
    private lateinit var chooseLod: Spinner
    private var lightIndicator:LightIndicatorView?=null
    private var running = false
    private var stableFrames = 0
    private var localPatchBusy = false
    private val localPatchFolder by lazy { LocalPatchDirectory(this) }
    private val patchFolderRequest=8124

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            Choreographer.getInstance().postFrameCallback(this)
            val submitStartNs=SystemClock.elapsedRealtimeNanos()
            cameraLight.applyToFilament()
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
    /**
     * All patch-defined windows, including their interactive controls, use one
     * renderer and a fixed whitelist of safe application state bindings.
     */
    private fun openPatchWindow(id: String) {
        val spec = uiChrome.windows[id] ?: return
        PatchWindowRenderer.show(
            this, spec, uiChrome.animations[id],
            readBinding = uiController::read,
            writeBinding = uiController::write,
            performAction = uiController::dispatch
        )
    }

    private fun updateButtonHighlights() {
        if(::toolbarController.isInitialized)toolbarController.updateHighlights()
    }
    private fun openSceneMenu() {
        sceneSettings.show()
    }

    private fun openMenu() {
        diagnosticsPopup.dismiss()
        // There is no user-visible sun color/style editor: only the verified
        // .pupdate asset module may change the unified technical appearance.
        GlassDialogFactory.menu(this,GlassDialogFactory.theme.titles.getValue("mainMenu"),
            GlassDialogFactory.theme.menuOrder.map { "Обновления" }) {
                GlassDialogFactory.message(this,"Обновления",
                    "Подписанные патчи проверяются автоматически через интернет. " +
                    "Локальный патч можно открыть из «Загрузок» через «Поделиться» → GlassStudy.")
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updater = PatchManager(this)
        updater.reconcileAfterCrash()
        initialWireframe=getPreferences(MODE_PRIVATE).getBoolean("wireframe",false)
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
        modelAssets=ModelMaterialController(viewer,updater)
        modelAssets.setAdaptivity(getPreferences(MODE_PRIVATE)
            .getInt("adaptivityPercent",0).coerceIn(0,100))
        sceneEnvironment=SceneEnvironmentController(this,viewer,getPreferences(MODE_PRIVATE)) {
            mode ->
            // A mode change must update *both* physical IBL and direct sunlight.
            renderEnvironment.setLightingEnvironment(mode,cameraLight.brightness,
                cameraLight.lightElevation,cameraLight.lightAz)
            cameraLight.applyToFilament()
        }
        sceneSettings=SceneSettingsPanel(this,sceneEnvironment)
        sceneEnvironment.applySkybox()
        renderEnvironment=RenderEnvironmentController(this,viewer)
        renderEnvironment.initialize()
        val preferences=getPreferences(MODE_PRIVATE)
        // Off by default: only the user's explicit toolbar choice enables FXAA.
        renderEnvironment.setFxaaOverride(preferences.getBoolean("fxaaOverride",false))
        cameraLight=CameraLightController(viewer,surface,{resources.displayMetrics.density},
            {mode,value -> uiChrome.showIndicator(mode,value,cameraLight.lightMode,
                cameraLight.yaw,cameraLight.pitch,cameraLight.lightAz,
                cameraLight.lightElevation,(cameraLight.brightness*100).toFloat())},
            {lightIndicator?.hide()},
            {factor,elevation,azimuth ->
                renderEnvironment.setLightingEnvironment(sceneEnvironment.lightingMode,
                    factor,elevation,azimuth)
                sceneEnvironment.setLightState(factor,elevation,azimuth)
            },
            {elevation -> renderEnvironment.directTransmission(elevation)},
            {elevation -> renderEnvironment.directColor(elevation)})
        surface.setOnTouchListener { _, event -> cameraLight.onTouch(event) }

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
            background=uiChrome.roundedFill(Color.rgb(19,41,54),30f,Color.argb(130,109,185,203))
        }
        val pillParams=LinearLayout.LayoutParams(-2,-2)
        pillParams.bottomMargin=d(6f)
        dock.addView(fpsPill,pillParams)

        bottomPanel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(d(11f),d(11f),d(11f),d(12f))
            background=uiChrome.roundedFill(Color.argb(220,35,47,58),19f,Color.argb(175,177,218,225))
            elevation=d(6f).toFloat()
        }
        selectorRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        chooseObject=Spinner(this).apply {
            adapter=uiChrome.selectionAdapter(ModelCatalog.names.toTypedArray())
            background=uiChrome.roundedFill(Color.rgb(20,42,57),13f,Color.argb(152,126,172,190))
        }
        val first=LinearLayout.LayoutParams(0,d(45f),1.35f)
        first.rightMargin=d(7f)
        selectorRow.addView(chooseObject,first)
        chooseLod=Spinner(this).apply {
            adapter=uiChrome.selectionAdapter(ModelCatalog.qualityNames.toTypedArray())
            background=uiChrome.roundedFill(Color.rgb(20,42,57),13f,Color.argb(152,126,172,190))
        }
        selectorRow.addView(chooseLod,LinearLayout.LayoutParams(0,d(45f),1f))
        // The quality spinner remains an internal selection state for compatibility;
        // the user changes LOD through the independent four-stop slider.
        chooseLod.visibility=View.GONE
        bottomPanel.addView(selectorRow)

        bottomButtonHost=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
        }
        // Independent quality and adaptivity controls. If both are open, quality
        // is always above adaptivity, regardless of button click order.
        qualityRow=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=View.GONE
            setPadding(d(5f),d(3f),d(5f),0)
        }
        qualityValue=TextView(this).apply {
            text="Качество: ${ModelCatalog.qualityNames[ModelCatalog.indexOfQuality(ModelCatalog.defaultSelection.lod)]}"
            textSize=11f
            setTextColor(Color.rgb(163,208,211))
        }
        qualitySlider=SeekBar(this).apply {
            max=ModelCatalog.qualities.lastIndex
            progress=ModelCatalog.indexOfQuality(ModelCatalog.defaultSelection.lod)
            contentDescription="Качество сетки: Low, Medium, High или Ultra"
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) {
                    val level=value.coerceIn(0,ModelCatalog.qualities.lastIndex)
                    qualityValue.text="Качество: ${ModelCatalog.qualityNames[level]}"
                    if(fromUser)uiController.write("qualityLevel",level.toFloat())
                }
                override fun onStartTrackingTouch(bar:SeekBar?) {}
                override fun onStopTrackingTouch(bar:SeekBar?) {}
            })
        }
        qualityRow.addView(qualityValue,LinearLayout.LayoutParams(-1,-2))
        qualityRow.addView(qualitySlider,LinearLayout.LayoutParams(-1,d(32f)))
        bottomPanel.addView(qualityRow,LinearLayout.LayoutParams(-1,-2))
        // The narrow on-demand control belongs to the dock, never floats over the bowl.
        adaptiveRow=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=View.GONE
            setPadding(d(5f),d(3f),d(5f),0)
        }
        adaptiveValue=TextView(this).apply {
            text="Адаптивность: ${modelAssets.adaptivePercent}%"
            textSize=11f
            setTextColor(Color.rgb(163,208,211))
        }
        adaptiveSlider=SeekBar(this).apply {
            max=100
            progress=modelAssets.adaptivePercent
            contentDescription="Адаптивность сетки, от 0 до 100 процентов"
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) {
                    adaptiveValue.text="Адаптивность: $value%"
                    if(!fromUser)return
                    pendingAdaptiveUpdate?.let { adaptiveRow.removeCallbacks(it) }
                    // Debounce GLB generation while dragging, apply final value on release.
                    val job=Runnable {
                        pendingAdaptiveUpdate=null
                        uiController.write("adaptivityPercent",value.toFloat())
                    }
                    pendingAdaptiveUpdate=job
                    adaptiveRow.postDelayed(job,110)
                }
                override fun onStartTrackingTouch(bar:SeekBar?) {}
                override fun onStopTrackingTouch(bar:SeekBar?) {
                    pendingAdaptiveUpdate?.let { adaptiveRow.removeCallbacks(it) }
                    pendingAdaptiveUpdate=null
                    uiController.write("adaptivityPercent",progress.toFloat())
                }
            })
        }
        adaptiveRow.addView(adaptiveValue,LinearLayout.LayoutParams(-1,-2))
        adaptiveRow.addView(adaptiveSlider,LinearLayout.LayoutParams(-1,d(32f)))
        bottomPanel.addView(adaptiveRow,LinearLayout.LayoutParams(-1,-2))
        // Buttons are always the LAST row; the quality slider is above
        // adaptivity when both are expanded. No overlay over the glass.
        bottomPanel.addView(bottomButtonHost,LinearLayout.LayoutParams(-1,-2))
        // No fixed Apply / Export / Diagnostics buttons below. All actions
        // are created by the signed declarative UI patch layout.
        dock.addView(bottomPanel)
        root.addView(dock,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        // Non-interactive HUD above the Filament SurfaceView.
        lightIndicator=LightIndicatorView(this).also { hud ->
            root.addView(hud,FrameLayout.LayoutParams(d(uiChrome.overlay.indicatorSizeDp.toFloat()),
                d(uiChrome.overlay.indicatorSizeDp.toFloat()),Gravity.CENTER))
        }

        toolbarController=UiToolbarController(this,topButtonHost,bottomButtonHost,topPanel,
            object:UiToolbarController.Host {
                override fun dispatch(action:String) { uiController.dispatch(action) }
                override fun isActive(action:String):Boolean=when(action) {
                    "toggleLight" -> cameraLight.lightMode
                    "toggleGrid" -> modelAssets.wireframe
                    "toggleFxaa" -> renderEnvironment.fxaaEnabled
                    "toggleQualityPanel" -> qualityRow.visibility==View.VISIBLE
                    "toggleAdaptivePanel" -> adaptiveRow.visibility==View.VISIBLE
                    "diagnostics" -> diagnosticsPopup.isShowing
                    else -> false
                }
                override fun decorate(view:View,selected:Boolean,color:Int,outline:Int?) {
                    uiChrome.skin(view,selected,color,outline)
                }
                override fun onLightLongPress() {
                    resetCameraToHome()
                    Toast.makeText(this@MainActivity,"Камера сброшена",Toast.LENGTH_SHORT).show()
                }
            })
        uiChrome.bind(UiChromeViews(mainRoot,topPanel,bottomPanel,
            logoMark,headerSubtitle,label,fpsPill,chooseObject,chooseLod,
            toolbarController,checkNotNull(lightIndicator)))
        chooseObject.setSelection(ModelCatalog.indexOfId(ModelCatalog.defaultSelection.modelId))
        chooseLod.setSelection(ModelCatalog.indexOfQuality(ModelCatalog.defaultSelection.lod))
        applyUiLayout()
        loadSelected()
        sceneEnvironment.applyFloor()
        // Immediately reload when a model or LOD is chosen, replacing "Применить".
        val onSelection=object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?, view:View?, position:Int, id:Long) {
                if (::viewer.isInitialized && ::updater.isInitialized &&
                    ModelCatalog.idAt(chooseObject.selectedItemPosition) != modelAssets.selection.modelId) {
                    loadSelected()
                }
            }
            override fun onNothingSelected(parent:AdapterView<*>?) {}
        }
        chooseObject.onItemSelectedListener=onSelection
        // The quality spinner remains internal for old patches. Its callbacks
        // must not overwrite or suppress the visible quality slider.
        // Local signed files take precedence; GitHub is optional fallback.
        if (!handleIncomingPatchIntent(intent)) checkLocalThenRemote()

    }

    /** Handle rotation without destroying Filament, camera state, or loaded models. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Remember the active panel before Android changes its viewport. A
        // rotation must not silently throw away a window or reset the scene.
        val patchToRestore = uiChrome.windows.keys.firstOrNull {
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
                        uiChrome.updateIndicatorVisibility(cameraLight.lightMode)
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

    private var lastPatchFolderScanUptimeMs=0L
    private fun checkLocalThenRemote() {
        if (localPatchBusy) return
        lastPatchFolderScanUptimeMs=android.os.SystemClock.uptimeMillis()
        val candidates=localPatchFolder.candidatesAbove(updater.installedPatchVersion())
        val scanError=localPatchFolder.lastScanError
        if (scanError != null) {
            updater.reportLocalScan("Ошибка чтения локальной папки: $scanError")
            // A transient storage-provider failure must not disable online updates.
            updater.checkOnStart(onCandidate={candidate -> applyVerifiedPatch(candidate,false)})
            return
        }
        if (candidates.isEmpty()) {
            if (localPatchFolder.configured) {
                updater.reportLocalScan("Локальная папка проверена: новых патчей нет")
            }
            // A connected but empty local folder is NOT a reason to skip remote patches.
            updater.checkOnStart(onCandidate={candidate -> applyVerifiedPatch(candidate,false)})
            return
        }
        // On a corrupt or incompatible latest file, try an older *newer-than-active*
        // candidate. Signature and SHA-256 are checked by the single PatchManager.
        localPatchBusy=true
        fun attempt(index:Int, failures:List<String>) {
            if (index >= candidates.size) {
                localPatchBusy=false
                updater.reportLocalScan("Локальные патчи отклонены: " +
                    failures.joinToString("; ").take(400))
                // If all local files are invalid, try the signed remote feed.
                updater.checkOnStart(onCandidate={candidate -> applyVerifiedPatch(candidate,false)})
                return
            }
            updater.installFromUri(candidates[index],
                onCandidate={candidate ->
                    localPatchBusy=false
                    applyVerifiedPatch(candidate,false)
                },
                onError={message ->
                    android.util.Log.w("GlassStudy","Local patch rejected: "+message)
                    attempt(index+1,failures + "файл ${index+1}: $message")
                })
        }
        attempt(0,emptyList())
    }

    @Deprecated("Activity result for persisted Storage Access Framework tree")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=patchFolderRequest)return
        if(resultCode==Activity.RESULT_OK && localPatchFolder.acceptGrant(data)) {
            Toast.makeText(this,"Папка патчей подключена — проверка при запуске включена",Toast.LENGTH_LONG).show()
            checkLocalThenRemote()
        } else Toast.makeText(this,"Доступ к папке не предоставлен",Toast.LENGTH_LONG).show()
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

    /** A single failure-safe path for both local and online signed updates. */
    private val patchActivation by lazy(LazyThreadSafetyMode.NONE) {
        PatchActivationCoordinator(updater,object:PatchActivationCoordinator.Host {
            override fun validate(candidate:PatchManager.Candidate) {
                PatchUiBundle.read(updater,candidate)
            }
            override fun previewModel(candidate:PatchManager.Candidate)=loadSelected(candidate)
            override fun previewUi(candidate:PatchManager.Candidate)=applyUiLayout(candidate)
            override fun restoreActive():Boolean {
                // Both restore steps run, even if one returns false or throws.
                val ui=runCatching { applyUiLayout() }.getOrDefault(false)
                val model=runCatching { loadSelected() }.getOrDefault(false)
                return ui && model
            }
        })
    }

    private fun applyVerifiedPatch(candidate:PatchManager.Candidate,fromDownloads:Boolean) {
        if(isFinishing || isDestroyed) {
            patchActivation.rejectUnavailable(candidate)
            return
        }
        val outcome=patchActivation.apply(candidate)
        if(outcome.installed) {
            stableFrames=0
            status()
            val message="Патч ${candidate.version} установлен, ожидает подтверждения кадром"
            if(fromDownloads) {
                GlassDialogFactory.message(this,
                    GlassDialogFactory.theme.titles.getValue("patchInstalled"),message)
            } else Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
        } else {
            android.util.Log.w("GlassStudy","Patch application rejected: "+outcome.message)
            if(fromDownloads) GlassDialogFactory.message(this,
                GlassDialogFactory.theme.titles.getValue("patchRejected"),outcome.message)
        }
    }

    /** Safe dynamic layout: add/remove/move operations use validated signed .pupdate JSON. */
    private fun applyUiLayout(candidate:PatchManager.Candidate?=null):Boolean {
        try {
            // All patch-controlled modules are parsed and validated before any UI mutation.
            val bundle = PatchUiBundle.read(updater, candidate)
            val ui=bundle.ui
            cameraLight.configure(bundle.camera,ui.zoomPower,ui.invertHorizontal)
            renderEnvironment.configureNoonSunLux(bundle.camera.lightIntensity)
            renderEnvironment.apply(bundle.render)
            sceneEnvironment.configureAtmosphereHaze(bundle.render.atmosphereHaze.toDouble())
            diagnosticsModule=bundle.diagnostics
            modelAssets.configure(bundle.materials)
            sceneEnvironment.applyConfiguration(bundle.scene)
            renderEnvironment.setLightingEnvironment(sceneEnvironment.lightingMode,
                cameraLight.brightness,cameraLight.lightElevation,cameraLight.lightAz)
            cameraLight.applyToFilament()
            uiChrome.apply(ui,bundle.windows,bundle.overlay)
            status()
            return true
        } catch(error:Exception) {
            android.util.Log.w("GlassStudy","Rejected UI patch",error)
            return false
        }
    }

    /**
     * Only the controller reads signed GLB variants and touches gltfio.
     * UI selection and grid changes are committed only on successful loading.
     */
    private fun loadSelected(
        candidate:PatchManager.Candidate?=null,
        debugOverride:Boolean?=null
    ):Boolean {
        val requested=ModelSelection(
            ModelCatalog.idAt(chooseObject.selectedItemPosition),
            ModelCatalog.qualityAt(qualitySlider.progress))
        val debug=debugOverride ?: if(modelAssets.hasLoaded) modelAssets.wireframe
            else initialWireframe
        val outcome=modelAssets.load(requested,debug,candidate)
        if(outcome.isSuccess) {
            // FXAA must never blur the topology wires; preserve the preference
            // for the normal rendered glass mode.
            renderEnvironment.setWireframeMode(modelAssets.wireframe)
            status()
            return true
        }
        val error=outcome.exceptionOrNull()
        if(candidate!=null) {
            android.util.Log.w("GlassStudy","Staged model failed to render",error)
        } else {
            if(modelAssets.hasLoaded) {
                val saved=modelAssets.selection
                chooseObject.setSelection(ModelCatalog.indexOfId(saved.modelId))
                chooseLod.setSelection(ModelCatalog.indexOfQuality(saved.lod))
            }
            GlassDialogFactory.message(this,GlassDialogFactory.theme.titles.getValue("modelError"),
                (error?.stackTraceToString() ?: "Ошибка загрузки модели").take(3000))
        }
        return false
    }
    private fun resetCameraToHome() {
        cameraLight.resetHome()
        status()
    }

    private fun status() {
        uiChrome.updateStatus(cameraLight.lightMode)
    }

    private fun showDiagnostic() {
        val report=diagnosticsReports.create(DiagnosticsReportState(
            diagnosticLabels=uiChrome.diagnosticLabels,
            diagnosticSections=uiChrome.diagnosticSections,
            diagnosticsModule=diagnosticsModule,
            renderModule=renderEnvironment.configuration,
            cameraModule=cameraLight.config,
            lightingMode=sceneEnvironment.lightingMode,
            modelId=modelAssets.selection.modelId,
            modelName=ModelCatalog.displayName(modelAssets.selection.modelId),
            modelLod=modelAssets.selection.lod,
            wireframe=modelAssets.wireframe,
            glassMaterialDetails=modelAssets.glassMaterialDiagnostics(),
            reflectionDetails=renderEnvironment.reflectionDiagnostics(),
            causticDetails=sceneEnvironment.causticDiagnostics,
            renderedFrames=viewer.renderedFrames.toString(),
            surfaceWidth=surface.width,
            surfaceHeight=surface.height,
            pixelDensity=resources.displayMetrics.density.toDouble(),
            distance=cameraLight.distance,
            yaw=cameraLight.yaw,
            pitch=cameraLight.pitch,
            lightAz=cameraLight.lightAz,
            lightElevation=cameraLight.lightElevation,
            brightness=cameraLight.brightness
        ))
        diagnosticsPopup.show(report,uiChrome.popupStyle())
    }
    override fun onResume() {
        super.onResume()
        // A new .pupdate may have arrived while GlassStudy was in the background.
        // After one Android system permission grant, scan Download without any picker.
        if (::updater.isInitialized && !localPatchBusy &&
            lastPatchFolderScanUptimeMs > 0L &&
            android.os.SystemClock.uptimeMillis()-lastPatchFolderScanUptimeMs >= 1200L) {
            checkLocalThenRemote()
        }
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
    override fun onPause() {
        cameraLight.resetGesture()
        diagnosticsPopup.dismiss()
        running=false
        profiler.resetAfterPause()
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onPause()
    }
    override fun onDestroy() {
        running=false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        if(::sceneEnvironment.isInitialized)sceneEnvironment.release()
        super.onDestroy()
    }
}