package com.glassstudy.filament

/** Work scheduling only: never changes samples, resolution, IOR or ray paths.
 * Completion wall time includes driver/scheduling delay; it is NOT GPU utilization.
 */
class OpticalWorkBudget {
    var percent:Int=50
        set(value) { field=value.coerceIn(10,100) }
    private var scale=1.0
    fun units(maximum:Int):Int=(maximum*percent/100.0*scale).toInt().coerceIn(1,maximum)
    fun observeCompletion(elapsedMs:Long) {
        if(elapsedMs>24)scale=(scale*12.0/elapsedMs).coerceAtLeast(1.0/32768.0)
        // Conservative growth avoids immediately repeating a long driver stall.
        else if(elapsedMs<10)scale=(scale*1.05).coerceAtMost(1.0)
    }
    fun pauseMs(elapsedMs:Long):Long=(elapsedMs.coerceIn(1,10000)*(100-percent)/percent).coerceAtMost(2000)
}
