package com.glassstudy.filament

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.filament.Camera
import com.google.android.filament.Skybox
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
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
    private lateinit var viewer: ModelViewer
    private lateinit var updater: PatchManager
    private var wireframe = false
    private lateinit var label: TextView
    private lateinit var topPanel: LinearLayout
    private lateinit var bottomPanel: LinearLayout
    private lateinit var selectorRow: LinearLayout
    private lateinit var actionsRow: LinearLayout
    private lateinit var applyButton: Button
    private lateinit var cameraButton: Button
    private lateinit var gridButton: Button
    private lateinit var exportButton: Button
    private lateinit var diagnosticButton: Button
    private var zoomPower = .50
    private var diagnosticLabels: Map<String,String> = emptyMap()
    private lateinit var chooseObject: Spinner
    private lateinit var chooseLod: Spinner
    private var currentId = "tumbler"
    private var currentLod = "medium"
    private var lightMode = false
    private var yaw = .38
    private var pitch = .22
    private var distance = 7.4
    private var lightAz = 1.3
    private var lightElevation = .9
    private var brightness = 1.0
    private var oldX = 0f
    private var oldY = 0f
    private var oldPinch = 0f
    private var prevFrameNs = 0L
    private var avgFps = 0.0
    private var running = false
    private var stableFrames = 0
    private var localPatchBusy = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            Choreographer.getInstance().postFrameCallback(this)
            if (prevFrameNs > 0 && frameTimeNanos > prevFrameNs) {
                val fps = 1e9 / (frameTimeNanos - prevFrameNs).toDouble()
                avgFps = if (avgFps == 0.0) fps else avgFps * .92 + fps * .08
            }
            prevFrameNs = frameTimeNanos
            updateCamera()
            viewer.render(frameTimeNanos)
            if (++stableFrames == 4) updater.confirmFrameRendered()
        }
    }

    private fun d(v: Float): Int = (v * resources.displayMetrics.density).toInt()
    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(32, 62, 79))
        setAllCaps(false)
        setOnClickListener { action() }
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

        viewer = ModelViewer(surface, manipulator = null)
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

        topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(185, 32, 49, 61))
            setPadding(d(8f), d(7f), d(8f), d(7f))
        }
        label = TextView(this).apply { textSize = 13f; setTextColor(Color.WHITE) }
        topPanel.addView(label)
        selectorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chooseObject = Spinner(this)
        chooseObject.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        selectorRow.addView(chooseObject, LinearLayout.LayoutParams(0, d(49f), 2.4f))
        chooseLod = Spinner(this)
        chooseLod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Low", "Medium", "High"))
        selectorRow.addView(chooseLod, LinearLayout.LayoutParams(0, d(49f), 1.15f))
        topPanel.addView(selectorRow)
        actionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        applyButton=button("Применить") { loadSelected() }
        actionsRow.addView(applyButton, LinearLayout.LayoutParams(0, d(48f), 1f))
        cameraButton=button("Свет / Камера") {
            lightMode = !lightMode
            status()
        }
        actionsRow.addView(cameraButton, LinearLayout.LayoutParams(0, d(48f), 1f))
        topPanel.addView(actionsRow)
        gridButton=button("Сетка: включить / выключить") {
            wireframe = !wireframe
            getPreferences(MODE_PRIVATE).edit().putBoolean("wireframe",wireframe).apply()
            loadSelected()
        }
        topPanel.addView(gridButton, LinearLayout.LayoutParams(-1,d(42f)))
        root.addView(topPanel, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        bottomPanel = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(d(8f),d(4f),d(8f),d(8f)) }
        exportButton=button("Экспорт сцены") { exportScene() }
        bottomPanel.addView(exportButton, LinearLayout.LayoutParams(0, d(52f), 1f))
        diagnosticButton=button("Диагностика") { showDiagnostic() }
        bottomPanel.addView(diagnosticButton, LinearLayout.LayoutParams(0, d(52f), 1f))
        root.addView(bottomPanel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        chooseObject.setSelection(1)
        chooseLod.setSelection(1)
        applyUiLayout()
        loadSelected()
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

    /** Called only with trusted bundled UI or a fully authenticated .pupdate candidate. */
    private fun applyUiLayout(candidate: PatchManager.Candidate? = null): Boolean {
        try {
            val bytes=updater.readUiConfig(candidate)
            val ui=UiLayout.parse(bytes)
            // Validate everything BEFORE mutating the existing controls.
            val sections=mapOf<String, View>("status" to label,"selectors" to selectorRow,
                "actions" to actionsRow,"grid" to gridButton)
            val bottomSections=mapOf<String, View>("export" to exportButton,
                "diagnostics" to diagnosticButton)
            zoomPower=ui.zoomPower
            diagnosticLabels=ui.diagnostics
            label.textSize=ui.textSizeSp
            label.setTextColor(ui.textColor)
            topPanel.setBackgroundColor(ui.topColor)
            topPanel.setPadding(d(ui.paddingDp.toFloat()),d(7f),d(ui.paddingDp.toFloat()),d(7f))
            bottomPanel.setBackgroundColor(ui.bottomColor)
            for (b in listOf(applyButton,cameraButton,gridButton,exportButton,diagnosticButton)) {
                b.setTextColor(ui.buttonTextColor)
                b.setBackgroundColor(ui.buttonColor)
            }
            applyButton.text=ui.applyText
            cameraButton.text=ui.cameraText
            gridButton.text=ui.gridText
            exportButton.text=ui.exportText
            diagnosticButton.text=ui.diagnosticText
            topPanel.removeAllViews()
            ui.topOrder.forEach { key ->
                val child=sections.getValue(key)
                val h=when(key) {"status" -> -2; "grid" -> d(ui.gridHeightDp.toFloat())
                    "actions" -> d(ui.actionHeightDp.toFloat());else -> d(49f)}
                topPanel.addView(child, LinearLayout.LayoutParams(-1,h))
            }
            bottomPanel.removeAllViews()
            ui.bottomOrder.forEach { key ->
                bottomPanel.addView(bottomSections.getValue(key), LinearLayout.LayoutParams(0,d(52f),1f))
            }
            return true
        } catch (e:Exception) {
            android.util.Log.w("GlassStudy", "Rejected UI layout patch", e)
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
    private fun updateCamera() {
        val cy = cos(pitch)
        val x = distance * cy * sin(yaw)
        val z = distance * cy * cos(yaw)
        val y = .95 + distance * sin(pitch)
        viewer.camera.lookAt(x, y, z, 0.0, .95, 0.0, 0.0, 1.0, 0.0)
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
                    else distance = (distance * (delta / oldPinch).toDouble().pow(zoomPower)).coerceIn(2.8, 60.0)
                }
                oldPinch = delta
            } else {
                val dx = (e.x-oldX)/max(1, surface.width)
                val dy = (e.y-oldY)/max(1, surface.height)
                if (lightMode) {
                    lightAz += dx * 7.0
                    lightElevation = (lightElevation - dy * 3.0).coerceIn(.08,1.55)
                } else {
                    yaw -= dx * 6.0
                    pitch = (pitch-dy * 4.0).coerceIn(-1.3,1.3)
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
            label.text = "GlassStudy Filament v${BuildConfig.VERSION_NAME} · $currentId/$currentLod\n$source"
        }
    }
    private fun showDiagnostic() {
        // All human-readable diagnostic labels come from signed ui/layout.json
        // or the bundled Russian fallback. A .pupdate can change these labels.
        fun tr(key: String): String = diagnosticLabels[key] ?: key
        val patchFromServer=updater.serverPatchVersion
        val knownServerVersion=if(patchFromServer > 0) patchFromServer.toString() else tr("unknown")
        val hasIbl=try {
            assets.open("envs/studio_ibl.ktx").close()
            true
        } catch (_: Exception) { false }
        val information=buildString {
            appendLine("GlassStudy v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("${tr("appVersion")}: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("${tr("installedPatch")}: ${updater.installedPatchVersion()}")
            appendLine("${tr("confirmedPatch")}: ${updater.lastConfirmedVersion()}")
            appendLine("${tr("serverPatch")}: $knownServerVersion")
            appendLine("${tr("updateCheck")}: ${updater.updateStatus}")
            appendLine("${tr("renderer")}: ${tr("rendererValue")}")
            appendLine("${tr("geometry")}: ${tr("geometryValue")}")
            appendLine("${tr("device")}: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("${tr("android")}: ${android.os.Build.VERSION.RELEASE}")
            appendLine("${tr("model")}: $currentId / $currentLod")
            appendLine("${tr("screen")}: ${surface.width} × ${surface.height}")
            appendLine("${tr("fps")}: ${"%.1f".format(java.util.Locale("ru", "RU"),avgFps)}")
            appendLine("${tr("glass")}: ${tr("glassValue")}")
            appendLine("${tr("ibl")}: ${tr(if(hasIbl) "iblAvailable" else "iblUnavailable")}")
            appendLine("${tr("pathTracing")}: ${tr("pathTracingValue")}")
        }
        AlertDialog.Builder(this).setTitle(tr("title"))
            .setMessage(information).setPositiveButton("ОК",null)
            .setNeutralButton("Копировать") { _, _ ->
                val cb=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("Диагностика GlassStudy",information))
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
    override fun onPause() { running=false;Choreographer.getInstance().removeFrameCallback(frameCallback); super.onPause() }
    override fun onDestroy() { running=false;Choreographer.getInstance().removeFrameCallback(frameCallback); super.onDestroy() }
}