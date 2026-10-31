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
        // Wall time includes scheduling, not just shader work. Never let one
        // compile/driver stall collapse the next batch by orders of magnitude.
        if(elapsedMs>32)scale=(scale*maxOf(.5,24.0/elapsedMs)).coerceAtLeast(minimumScale)
        // Recover through normal 10..23 ms scheduling intervals too. The old
        // <10 ms gate could permanently trap one-photon batches on the phone.
        else if(elapsedMs<24)scale=(scale*1.25).coerceAtMost(1.0)
    }
    fun pauseMs(elapsedMs:Long):Long=(elapsedMs.coerceIn(1,10000)*(100-percent)/percent).coerceAtMost(2000)
}
