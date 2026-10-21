package com.glassstudy.filament

import org.json.JSONObject

/**
 * Numeric presentation values for ALL three passive light indicators.
 * The signed overlay module carries these as ordinary JSON data: no Kotlin
 * code, new View class, or arbitrary command is loaded from a patch.
 *
 * Geometry values use the original 230-unit canvas coordinate system.
 * Offsets use dp so moving an indicator remains stable across view sizes.
 */
data class IndicatorVisualConfig(
    val offsetXDp:Float=0f,
    val offsetYDp:Float=0f,
    val rotationAtMaxDeg:Float=0f,
    val travelHalfUnits:Float=63f,
    val guideRadiusUnits:Float=71f,
    val guideGapUnits:Float=24f,
    val sunRadiusUnits:Float=9f,
    val rayStartUnits:Float=14f,
    val rayLengthMinUnits:Float=3f,
    val rayLengthMaxUnits:Float=12f,
    val guideStrokeUnits:Float=2.3f,
    val sunStrokeUnits:Float=2.4f,
    val captionYUnits:Float=97f,
    val captionSizeUnits:Float=16f,
    val smoothing:Float=.28f,
    val settleThreshold:Float=.08f,
    val fadeInMs:Long=140L,
    val fadeOutMs:Long=160L
) {
    companion object {
        fun defaults(mode:LightIndicatorView.Mode):IndicatorVisualConfig =
            if(mode==LightIndicatorView.Mode.BRIGHTNESS)
                IndicatorVisualConfig(offsetXDp=40f,rotationAtMaxDeg=90f)
            else IndicatorVisualConfig(rayLengthMinUnits=6f,rayLengthMaxUnits=6f)

        private val ranges=mapOf(
            // Wider safe range: actual movement is on the parent View, no Canvas clipping.
            "offsetXDp" to (-160.0..160.0),
            "offsetYDp" to (-80.0..80.0),
            "rotationAtMaxDeg" to (-360.0..360.0),
            "travelHalfUnits" to (10.0..90.0),
            "guideRadiusUnits" to (25.0..95.0),
            "guideGapUnits" to (8.0..42.0),
            "sunRadiusUnits" to (4.0..22.0),
            "rayStartUnits" to (9.0..35.0),
            "rayLengthMinUnits" to (0.0..35.0),
            "rayLengthMaxUnits" to (0.0..45.0),
            "guideStrokeUnits" to (0.5..8.0),
            "sunStrokeUnits" to (0.5..8.0),
            "captionYUnits" to (75.0..110.0),
            "captionSizeUnits" to (8.0..28.0),
            "smoothing" to (0.05..1.0),
            "settleThreshold" to (0.005..1.0),
            "fadeInMs" to (0.0..1200.0),
            "fadeOutMs" to (0.0..1200.0)
        )

        fun parse(j:JSONObject?,mode:LightIndicatorView.Mode):IndicatorVisualConfig {
            val d=defaults(mode)
            if(j==null)return d
            require(j.keys().asSequence().all { it in ranges }) {
                "Unknown light indicator numeric property"
            }
            fun n(key:String, fallback:Float):Float {
                val raw=j.opt(key) ?: return fallback
                require(raw is Number) { "Indicator property must be numeric: $key" }
                val value=raw.toDouble()
                require(value.isFinite() && value in requireNotNull(ranges[key])) {
                    "Indicator property out of range: $key"
                }
                return value.toFloat()
            }
            fun ms(key:String, fallback:Long):Long {
                val v=n(key,fallback.toFloat())
                require(v%1f==0f) { "Duration must be a whole number: $key" }
                return v.toLong()
            }
            val result=IndicatorVisualConfig(
                n("offsetXDp",d.offsetXDp),
                n("offsetYDp",d.offsetYDp),
                n("rotationAtMaxDeg",d.rotationAtMaxDeg),
                n("travelHalfUnits",d.travelHalfUnits),
                n("guideRadiusUnits",d.guideRadiusUnits),
                n("guideGapUnits",d.guideGapUnits),
                n("sunRadiusUnits",d.sunRadiusUnits),
                n("rayStartUnits",d.rayStartUnits),
                n("rayLengthMinUnits",d.rayLengthMinUnits),
                n("rayLengthMaxUnits",d.rayLengthMaxUnits),
                n("guideStrokeUnits",d.guideStrokeUnits),
                n("sunStrokeUnits",d.sunStrokeUnits),
                n("captionYUnits",d.captionYUnits),
                n("captionSizeUnits",d.captionSizeUnits),
                n("smoothing",d.smoothing),
                n("settleThreshold",d.settleThreshold),
                ms("fadeInMs",d.fadeInMs),
                ms("fadeOutMs",d.fadeOutMs)
            )
            require(result.rayLengthMinUnits<=result.rayLengthMaxUnits) {
                "Ray minimum length exceeds maximum"
            }
            require(result.rayStartUnits>result.sunRadiusUnits) {
                "Rays must start outside sun circle"
            }
            return result
        }
    }
}
