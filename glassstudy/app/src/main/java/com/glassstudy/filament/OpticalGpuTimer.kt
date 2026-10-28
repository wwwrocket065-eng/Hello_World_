package com.glassstudy.filament

/** Optional 64-bit EXT timer. GL-thread-only, bounded asynchronous queries.
 * No glFinish, readPixels, wait loop, or extra rendering to obtain measurements.
 */
class OpticalGpuTimer(private val metrics:OpticalRenderMetrics) {
    private data class Pending(val id:Int,val stage:OpticalRenderMetrics.Stage,val run:Long,val issuedNs:Long)
    private val pending=java.util.ArrayDeque<Pending>()
    private var enabled=false
    private var active:Pending?=null
    private var skipped=0L
    private external fun nativeInit():Int
    private external fun nativeBegin():Int
    private external fun nativeEnd()
    private external fun nativeDelete(id:Int)
    private external fun nativeDisjoint():Boolean
    private external fun nativeResult(id:Int):Long
    companion object {
        private val loaded=try { System.loadLibrary("glassstudy_optical_timer");true } catch(_:LinkageError) { false }
    }
    fun contextCreated(extensions:String) {
        // Names belong to the destroyed context. Do NOT delete them in a new context.
        pending.clear();active=null;enabled=false;skipped=0
        if(!loaded || "GL_EXT_disjoint_timer_query" !in extensions.split(' ')) {
            metrics.gpuStatus("недоступно: драйвер/64-битный модуль не поддерживает EXT_disjoint_timer_query")
            return
        }
        val bits=nativeInit();enabled=bits>=64
        metrics.gpuStatus(if(enabled)"EXT_disjoint_timer_query, ${bits}-битный счётчик; асинхронно"
            else "недоступно: счётчик $bits бит, длинные вызовы могут переполниться")
    }
    fun begin(stage:OpticalRenderMetrics.Stage) {
        if(!enabled)return
        if(pending.size>=64) { skipped++;metrics.gpuSkipped(skipped);return }
        val id=nativeBegin()
        if(id!=0)active=Pending(id,stage,metrics.snapshot().run,System.nanoTime())
    }
    fun end() {
        val p=active ?: return
        nativeEnd();pending.addLast(p);active=null
    }
    fun poll() {
        if(!enabled)return
        if(nativeDisjoint()) { discard("GPU disjoint: недостоверные замеры отброшены");return }
        val ready=mutableListOf<Pair<Pending,Long>>()
        while(pending.isNotEmpty()) {
            val p=pending.first
            val duration=nativeResult(p.id)
            if(duration==-1L)break
            pending.removeFirst();nativeDelete(p.id)
            if(duration>=0)ready.add(p to duration)
            else metrics.event("GPU timer: недопустимый результат отброшен")
        }
        if(nativeDisjoint()) { discard("GPU disjoint: недостоверные замеры отброшены");return }
        for((p,duration) in ready)metrics.recordGpu(p.stage,p.run,p.issuedNs,duration)
    }
    fun pause() { if(enabled)discard("GPU timer: пауза, незавершённые замеры отброшены") }
    private fun discard(reason:String) {
        for(p in pending)nativeDelete(p.id)
        pending.clear();metrics.event(reason)
    }
}
