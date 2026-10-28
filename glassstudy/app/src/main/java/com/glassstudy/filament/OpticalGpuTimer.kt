package com.glassstudy.filament

/** Optional 64-bit EXT timer. GL-thread-only, bounded asynchronous queries.
 * No glFinish, readPixels, wait loop, or extra rendering to obtain measurements.
 */
class OpticalGpuTimer(private val metrics:OpticalRenderMetrics) {
    private data class Pending(val id:Int,val stage:OpticalRenderMetrics.Stage,val run:Long,val issuedNs:Long)
    private val pending=java.util.ArrayDeque<Pending>()
    private var enabled=false
    private var requested=true
    private var supported=false
    private var bits=0
    fun configure(value:Boolean) {
        requested=value
        if(!value && enabled)discard("GPU-таймер отключён конфигурацией")
        enabled=supported && requested
        updateStatus()
    }
    private fun updateStatus() {
        metrics.gpuStatus(when {
            !requested -> "отключён подписанной конфигурацией"
            supported -> "EXT_disjoint_timer_query, ${bits}-битный счётчик; асинхронно"
            bits>0 -> "недоступно: счётчик $bits бит, длинные вызовы могут переполниться"
            else -> "недоступно: драйвер/модуль не поддерживает EXT_disjoint_timer_query"
        })
    }
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
        pending.clear();active=null;enabled=false;supported=false;bits=0;skipped=0
        if(!loaded || "GL_EXT_disjoint_timer_query" !in extensions.split(' ')) {
            updateStatus()
            return
        }
        bits=nativeInit();supported=bits>=64;enabled=supported && requested
        updateStatus()
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
