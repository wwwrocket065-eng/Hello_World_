package com.glassstudy.filament

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Frozen application state passed to an on-demand, read-only diagnostic report. */
data class DiagnosticsReportState(
    val diagnosticLabels:Map<String,String>,
    val diagnosticSections:List<String>,
    val diagnosticsModule:DiagnosticsModuleConfig,
    val renderModule:RenderModuleConfig,
    val cameraModule:CameraModuleConfig,
    val lightingMode:LightEnvironmentMode,
    val modelId:String,
    val modelName:String,
    val modelLod:String,
    val wireframe:Boolean,
    val glassMaterialDetails:String,
    val reflectionDetails:String,
    val renderedFrames:String,
    val surfaceWidth:Int,
    val surfaceHeight:Int,
    val pixelDensity:Double,
    val distance:Double,
    val yaw:Double,
    val pitch:Double,
    val lightAz:Double,
    val lightElevation:Double,
    val brightness:Double
)

/** Measures real metrics on demand; never reports unmeasured GPU utilization. */
class DiagnosticsReportService(
    private val context:Context,
    private val updater:PatchManager,
    private val profiler:FrameProfiler
) {
    fun create(state:DiagnosticsReportState):String {
        fun tr(key:String)=state.diagnosticLabels[key] ?: key
        fun num(x:Double?)=if (x==null || !x.isFinite()) "—"
            else String.format(Locale("ru","RU"),"%.1f",x)
        val perf=profiler.metrics()
        val ram=try { MemoryMetrics.capture(context) } catch (_:Exception) { null }
        val mesh=try {
            val profile=updater.readResourceOrNull(ParametricGoblet.PATH)
                ?: error("Missing goblet profile")
            GlbMetrics.inspect(ParametricGoblet.create(profile,state.modelLod,state.wireframe))
        } catch (_:Exception) { null }
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
        // Computed from the exact same physical model as the renderer.
        // These are modeled values, NOT measured sensor/GPU readings.
        val physicalSkyLux=PhysicalAtmosphereModel.skyIlluminance(state.lightingMode,
            state.lightElevation,state.renderModule.atmosphereSkyIlluminance.toDouble(),
            state.renderModule.atmosphereHorizonFraction.toDouble())*
            LightingResponseCurve.intensity(state.brightness,
                state.renderModule.globalBrightnessExponent.toDouble())
        val physicalSunLux=state.cameraModule.lightIntensity*
            LightingResponseCurve.intensity(state.brightness,
                state.cameraModule.directBrightnessExponent)*
            PhysicalAtmosphereModel.solarTransmission(state.lightingMode,
                state.lightElevation,state.renderModule.atmosphereExtinction.toDouble())
        val effectiveIso=PhysicalExposureModel.sensitivity(state.lightingMode,
            state.lightElevation,state.cameraModule.lightIntensity,
            state.renderModule.atmosphereExtinction.toDouble(),
            state.renderModule.atmosphereSkyIlluminance.toDouble(),
            state.renderModule.atmosphereHorizonFraction.toDouble(),
            state.renderModule.exposureSensitivity.toDouble(),
            state.renderModule.atmosphereExposureReferenceLux.toDouble(),
            state.renderModule.atmosphereExposureMaxGain.toDouble())
        val stamp=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale("ru","RU")).format(Date())
        val report=buildString {
            // Version appears ONCE. Patch numbers occupy ONE line.
            appendLine("GlassStudy ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · $stamp")
            for (section in state.diagnosticSections) {
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
                        appendLine("${tr("model")}: ${state.modelName} · ${state.modelLod} · ${tr("gridMode")}: ${if(state.wireframe) tr("yes") else tr("no")}")
                        if(mesh != null) {
                            appendLine("GLB: ${mesh.vertices} ${tr("vertices")} · ${mesh.triangles} ${tr("triangles")}${if(mesh.segments>0) " · ${mesh.segments} ${tr("segments")}" else ""}")
                            appendLine("${mesh.primitives} ${tr("primitives")} · ${num(mesh.bytes/1024.0)} ${tr("kilobytes")} · SHA256: ${mesh.sha12}…")
                        } else appendLine("${tr("geometry")}: ${tr("notMeasured")}")
                    }
                    "renderer" -> {
                        appendLine()
                        appendLine("▸ ${tr("sectionRenderer")}")
                        appendLine("Google Filament / gltfio · OpenGL ES")
                        appendLine("Реально нарисованных кадров Filament: ${state.renderedFrames}")
                        appendLine("${tr("glass")}: ${tr("glassValue")}")
                        appendLine("Материал gltfio (фактически): ${state.glassMaterialDetails}")
                        appendLine("Режим света: ${if(state.lightingMode==LightEnvironmentMode.VACUUM) "Вакуум" else "Атмосфера"}")
                        appendLine("IBL: ${state.reflectionDetails}")
                        appendLine("Расчётный солнечный поток: ${num(physicalSunLux)} лк · рассеянный свет: ${num(physicalSkyLux)} лк")
                        appendLine("Экспозиция камеры: ISO ${num(effectiveIso.toDouble())} · адаптация ${if(state.lightingMode==LightEnvironmentMode.ATMOSPHERE) "по небу и солнцу" else "отключена"}")
                        appendLine("Студийные HDRI: отключены · каустики: отдельная быстрая аппроксимация")
                        appendLine("Тени стекла: сплошная чёрная отключена; световой рисунок на полу приближённый")
                        appendLine("${tr("pathTracing")}: ${tr("pathTracingValue")}")
                    }
                    "camera" -> {
                        appendLine()
                        appendLine("▸ ${tr("sectionCamera")}")
                        appendLine("${tr("distance")}: ${num(state.distance)} · ${tr("yaw")}: ${num(Math.toDegrees(state.yaw))}° · ${tr("pitch")}: ${num(Math.toDegrees(state.pitch))}°")
                        appendLine("${tr("lightAngles")}: ${num(Math.toDegrees(state.lightAz))}° / ${num(Math.toDegrees(state.lightElevation))}° · ×${num(state.brightness)}")
                    }
                    "device" -> {
                        appendLine()
                        appendLine("▸ ${tr("sectionDevice")}")
                        appendLine("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}")
                        appendLine("${tr("screen")}: ${state.surfaceWidth} × ${state.surfaceHeight} · DPR ${num(state.pixelDensity.toDouble())}")
                        appendLine("${tr("gpuInfo")}: ${tr("notMeasured")}")
                    }
                }
            }
            appendLine()
            if (state.diagnosticsModule.includeModuleStatus) {
                appendLine("▸ МОДУЛИ")
                appendLine("Камера / Рендер / Диагностика: конфигурация загружена")
                appendLine("Активная версия ресурсов: $active")
            }
            if (state.diagnosticsModule.includeRenderParameters) {
                appendLine("▸ НАСТРОЙКИ РЕНДЕРА")
                appendLine("Атмосферный небесный свет при зените: ${num(state.renderModule.atmosphereSkyIlluminance.toDouble())} лк · FXAA: ${if(state.renderModule.fxaa) "ВКЛ" else "ВЫКЛ"}")
                appendLine("Экспозиция: f/${num(state.renderModule.exposureAperture.toDouble())} · ISO ${num(state.renderModule.exposureSensitivity.toDouble())}")
            }
            if (state.diagnosticsModule.includeCameraParameters) {
                appendLine("▸ НАСТРОЙКИ КАМЕРЫ")
                appendLine("FOV: ${num(state.cameraModule.fieldOfView)}° · диапазон: ${num(state.cameraModule.minDistance)}–${num(state.cameraModule.maxDistance)}")
            }
            append(tr("measurementNote"))
        }
        return report
    }
}
