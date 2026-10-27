package com.glassstudy.filament

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Read-only, low-overhead process/frame instrumentation.
 * Choreographer timings are display pacing, NOT true GPU execution times.
 * CPU process time uses elapsed time across ALL app threads and may exceed 100%
 * when multiple cores are active. Sampling does not request extra permissions.
 */
class FrameProfiler {
    private val intervalsMs = DoubleArray(180)
    private var written = 0
    private var lastFrameNs = 0L
    private var submissionAvgMs = 0.0
    private var submissionSamples = 0
    private var startWallNs = 0L
    private var startCpuMs = 0L
    var processCpuPercent: Double? = null
        private set

    fun frame(frameTimeNs: Long, submitMs: Double) {
        if (lastFrameNs > 0L) {
            val delta = (frameTimeNs - lastFrameNs) / 1_000_000.0
            if (delta in 1.0..1000.0) {
                intervalsMs[written % intervalsMs.size] = delta
                written++
            }
        }
        lastFrameNs = frameTimeNs
        if (submitMs.isFinite() && submitMs in 0.0..1000.0) {
            submissionAvgMs = if (submissionSamples == 0) submitMs
                else submissionAvgMs * 0.92 + submitMs * 0.08
            submissionSamples++
        }
        val now=SystemClock.elapsedRealtimeNanos()
        if (startWallNs == 0L) {
            startWallNs=now
            startCpuMs=Process.getElapsedCpuTime()
        } else if (now - startWallNs >= 1_000_000_000L) {
            val elapsedMs=(now - startWallNs) / 1_000_000.0
            val cpuMs=Process.getElapsedCpuTime()
            val cpuDelta=(cpuMs-startCpuMs).coerceAtLeast(0L)
            processCpuPercent=(cpuDelta * 100.0 / elapsedMs).coerceAtLeast(0.0)
            startWallNs=now
            startCpuMs=cpuMs
        }
    }

    fun resetAfterPause() {
        lastFrameNs=0L
        startWallNs=0L
    }

    fun metrics(): FrameMetrics {
        val sampleCount=written.coerceAtMost(intervalsMs.size)
        if (sampleCount == 0) return FrameMetrics(null,null,null,null,
            if (submissionSamples > 0) submissionAvgMs else null, processCpuPercent)
        val samples=intervalsMs.copyOfRange(0,sampleCount).sorted()
        val mean=samples.average()
        val p95=samples[((samples.size-1)*0.95).roundToInt()]
        return FrameMetrics(1000.0/mean,mean,p95,sampleCount,
            if (submissionSamples > 0) submissionAvgMs else null,processCpuPercent)
    }
}

data class FrameMetrics(
    val fps: Double?, val avgFrameMs: Double?, val p95FrameMs: Double?,
    val frames: Int?, val submissionMs: Double?, val cpuPercent: Double?
)

data class MemoryMetrics(
    val pssMb: Double, val javaHeapMb: Double, val nativeHeapMb: Double,
    val freeDeviceMb: Double, val totalDeviceMb: Double
) {
    companion object {
        fun capture(context: Context): MemoryMetrics {
            val memory=Debug.MemoryInfo()
            Debug.getMemoryInfo(memory)
            val rt=Runtime.getRuntime()
            val activity=context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val system=ActivityManager.MemoryInfo()
            activity.getMemoryInfo(system)
            return MemoryMetrics(
                memory.totalPss / 1024.0,
                (rt.totalMemory()-rt.freeMemory()) / (1024.0*1024.0),
                Debug.getNativeHeapAllocatedSize() / (1024.0*1024.0),
                system.availMem / (1024.0*1024.0),
                system.totalMem / (1024.0*1024.0))
        }
    }
}

/** Reports attributes in the actual GLB loaded by Filament, not generated guesses. */
data class GlbMetrics(val vertices: Int, val triangles: Int, val segments: Int,
    val primitives: Int, val bytes: Int, val sha12: String) {
    companion object {
        fun inspect(raw: ByteArray): GlbMetrics {
            require(raw.size >= 20) { "GLB header missing" }
            val header=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            require(header.getInt(0)==0x46546c67 && header.getInt(4)==2 && header.getInt(8)==raw.size) {
                "Not glTF 2.0 GLB"
            }
            val jsonLen=header.getInt(12)
            require(jsonLen in 2..(raw.size-20)) { "Corrupt GLB JSON length" }
            require(header.getInt(16)==0x4e4f534a) { "First GLB chunk is not JSON" }
            val json=JSONObject(String(raw,20,jsonLen,Charsets.UTF_8))
            val accessors=json.getJSONArray("accessors")
            val meshes=json.getJSONArray("meshes")
            var vertices=0;var triangles=0;var segments=0;var count=0
            for(i in 0 until meshes.length()) {
                val primitives=meshes.getJSONObject(i).getJSONArray("primitives")
                for(j in 0 until primitives.length()) {
                    val primitive=primitives.getJSONObject(j)
                    val pos=primitive.getJSONObject("attributes").getInt("POSITION")
                    vertices+=accessors.getJSONObject(pos).getInt("count")
                    val n=if(primitive.has("indices"))
                        accessors.getJSONObject(primitive.getInt("indices")).getInt("count")
                        else accessors.getJSONObject(pos).getInt("count")
                    when(primitive.optInt("mode",4)) {
                        4 -> triangles+=n/3
                        1 -> segments+=n/2
                        3 -> segments+=(n-1).coerceAtLeast(0)
                    }
                    count++
                }
            }
            val sha=MessageDigest.getInstance("SHA-256").digest(raw)
                .take(6).joinToString("") { "%02x".format(it) }
            return GlbMetrics(vertices,triangles,segments,count,raw.size,sha)
        }
    }
}
