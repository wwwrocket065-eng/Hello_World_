package com.glassstudy.filament

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
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
    private lateinit var chooseObject: Spinner
    private lateinit var chooseLod: Spinner
    private var currentId = "tumbler"
    private var currentLod = "medium"
    private var lightMode = false
    private var yaw = .38
    private var pitch = .22
    private var distance = 4.4
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

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(185, 32, 49, 61))
            setPadding(d(8f), d(7f), d(8f), d(7f))
        }
        label = TextView(this).apply { textSize = 13f; setTextColor(Color.WHITE) }
        top.addView(label)
        val control = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chooseObject = Spinner(this)
        chooseObject.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        control.addView(chooseObject, LinearLayout.LayoutParams(0, d(49f), 2.4f))
        chooseLod = Spinner(this)
        chooseLod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Low", "Medium", "High"))
        control.addView(chooseLod, LinearLayout.LayoutParams(0, d(49f), 1.15f))
        top.addView(control)
        val topButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topButtons.addView(button("Применить") { loadSelected() }, LinearLayout.LayoutParams(0, d(48f), 1f))
        topButtons.addView(button("Свет / Камера") {
            lightMode = !lightMode
            status()
        }, LinearLayout.LayoutParams(0, d(48f), 1f))
        top.addView(topButtons)
        top.addView(button("Сетка: включить / выключить") {
            wireframe = !wireframe
            getPreferences(MODE_PRIVATE).edit().putBoolean("wireframe",wireframe).apply()
            loadSelected()
        }, LinearLayout.LayoutParams(-1,d(42f)))
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(d(8f),d(4f),d(8f),d(8f)) }
        bottom.addView(button("Экспорт сцены") { exportScene() }, LinearLayout.LayoutParams(0, d(52f), 1f))
        bottom.addView(button("Диагностика") { showDiagnostic() }, LinearLayout.LayoutParams(0, d(52f), 1f))
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        chooseObject.setSelection(1)
        chooseLod.setSelection(1)
        loadSelected()
        // Automatic, non-blocking update check. No update button or manual install.
        updater.checkOnStart(onCandidate = { candidate ->
            if (isFinishing || isDestroyed) updater.reject(candidate)
            else if (loadSelected(candidate)) {
                if (updater.activate(candidate)) {
                    stableFrames = 0
                    status()
                    Toast.makeText(this, "Модели обновлены", Toast.LENGTH_SHORT).show()
                } else {
                    updater.reject(candidate)
                    loadSelected()
                }
            } else {
                updater.reject(candidate)
                loadSelected()
            }
        })
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
                    else distance = (distance * (delta / oldPinch)).coerceIn(2.3, 60.0)
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
        val support = "GlassStudy v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nInstalled asset patch: ${updater.installedPatchVersion()}\n" +
            "Renderer: official Google Filament / gltfio\n" +
            "Geometry: same GLB used by preview and export\n" +
            "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
            "Android: ${android.os.Build.VERSION.RELEASE}\n" +
            "Model: $currentId / $currentLod\n" +
            "Screen: ${surface.width} x ${surface.height}\n" +
            "FPS (display frame pacing, not GPU load): ${"%.1f".format(avgFps)}\n" +
            "Physical glass glTF: transmission=1, IOR=1.52, volume enabled\n" +
            "Specular IBL: ${try { assets.open("envs/studio_ibl.ktx").close(); "available" } catch (_: Exception) { "not bundled; using light fallback" }}\n" +
            "Final path tracing: not implemented in this native beta\n"
        AlertDialog.Builder(this).setTitle("Диагностика GlassStudy")
            .setMessage(support).setPositiveButton("OK",null)
            .setNeutralButton("Копировать") { _, _ ->
                val cb=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("GlassStudy diagnostics",support))
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
        if (::viewer.isInitialized) {
            running = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

    }
    override fun onPause() { running=false;Choreographer.getInstance().removeFrameCallback(frameCallback); super.onPause() }
    override fun onDestroy() { running=false;Choreographer.getInstance().removeFrameCallback(frameCallback); super.onDestroy() }
}