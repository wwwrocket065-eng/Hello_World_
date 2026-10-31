package com.glassstudy.filament

/** Work scheduling only: never changes samples, resolution, IOR or ray paths.
 * Completion wall time includes driver/scheduling delay; it is NOT GPU utilization.
 */
class OpticalWorkBudget {
    var percent:Int=50
        set(value) { field=value.coerceIn(10,100) }
    private var scale=1.0
    private var minimumScale=1.0/32768.0
    fun units(maximum:Int,minimum:Int=1):Int {
        val floor=minimum.coerceIn(1,maximum)
        minimumScale=maxOf(minimumScale,(floor*100.0/(maximum*percent)).coerceIn(1.0/32768.0,1.0))
        return (maximum*percent/100.0*scale).toInt().coerceIn(floor,maximum)
    }
    fun observeCompletion(elapsedMs:Long) {
        if(elapsedMs>24)scale=(scale*12.0/elapsedMs).coerceAtLeast(minimumScale)
        // Conservative growth avoids immediately repeating a long driver stall.
        else if(elapsedMs<10)scale=(scale*1.05).coerceAtMost(1.0)
    }
    fun pauseMs(elapsedMs:Long):Long=(elapsedMs.coerceIn(1,10000)*(100-percent)/percent).coerceAtMost(2000)
}
