package com.glassstudy.filament

import org.json.JSONObject

/** Bounded numeric configuration embedded in the signed render module. */
data class GlassOpticsConfig(
    val enabled:Boolean=true,
    val width:Int=1280,
    val samples:Int=128,
    val bounces:Int=16,
    val photonMap:Int=128,
    val photonSide:Int=384,
    val photonExtent:Float=4f,
    val settleMs:Long=350,
    val exposure:Float=3f,
    val movingWidth:Int=256,
    val pixelBudget:Int=131072,
    val maxSampleRadiance:Float=8f,
    val photonMargin:Float=.5f
) {
    companion object {
        fun parse(json:JSONObject?):GlassOpticsConfig {
            if(json==null)return GlassOpticsConfig()
            fun integer(key:String,default:Int,range:IntRange):Int {
                val v=json.optDouble(key,default.toDouble())
                require(v.isFinite() && v%1.0==0.0 && v>=range.first && v<=range.last) { "Invalid optics $key" }
                return v.toInt()
            }
            fun number(key:String,default:Float,low:Float,high:Float):Float {
                val v=json.optDouble(key,default.toDouble())
                require(v.isFinite() && v>=low && v<=high) { "Invalid optics $key" }
                return v.toFloat()
            }
            return GlassOpticsConfig(json.optBoolean("enabled",true),
                integer("width",1280,128..2048),integer("samples",128,1..1024),
                integer("bounces",16,4..24),integer("photonMap",128,64..256),
                integer("photonSide",384,128..768),number("photonExtent",4f,2f,12f),
                integer("settleMs",350,100..2000).toLong(),number("exposure",3f,.1f,32f),
                integer("movingWidth",256,128..512),integer("pixelBudget",131072,32768..524288),
                number("maxSampleRadiance",8f,1f,128f),number("photonMargin",.5f,.125f,4f))
        }
    }
}
