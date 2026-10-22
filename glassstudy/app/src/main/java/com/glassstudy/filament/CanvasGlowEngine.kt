package com.glassstudy.filament

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.roundToInt

/**
 * Shared UI effects renderer. Nothing here knows about lighting indicators,
 * suns, tools, button shapes or current interaction modes.
 *
 * Reuse for arbitrary Canvas Paths or center points with GlowEffectConfig
 * from any validated signed resource. Does not change Filament 3D shaders.
 */
class CanvasGlowEngine {
    private val strokePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE
        strokeCap=Paint.Cap.ROUND
        strokeJoin=Paint.Join.ROUND
    }
    private val areaPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.FILL
    }

    private fun withAlpha(rgb:Int,opacity:Float):Int {
        val effective=(opacity.coerceIn(0f,1f)*Color.alpha(rgb)).roundToInt().coerceIn(0,255)
        return Color.argb(effective,Color.red(rgb),Color.green(rgb),Color.blue(rgb))
    }

    /**
     * Stacked feathered strokes, widest and faintest first. The outer layer
     * starts at alpha ZERO and the brightness rises smoothly toward the edge
     * of the real shape. Sharp visible borders belong ONLY to the final glyph.
     */
    fun drawStroke(canvas:Canvas,path:Path,color:Int,baseWidth:Float,layer:GlowLayerConfig) {
        if(layer.radiusUnits<=0f || layer.opacity<=0f || Color.alpha(color)==0)return
        val n=layer.samples
        strokePaint.clearShadowLayer()
        for(i in n-1 downTo 1) {
            val spread=i.toFloat()/n
            val intensity=GlowFalloffMath.alphaAt(spread,layer.opacity,layer.falloff)
            // Subdivide alpha across the feather layers to avoid saturated neon.
            val alpha=intensity*3.0f/n
            if(alpha<=.0005f)continue
            strokePaint.strokeWidth=baseWidth+2f*layer.radiusUnits*spread
            strokePaint.color=withAlpha(color,alpha)
            canvas.drawPath(path,strokePaint)
        }
    }

    /**
     * Draw a glow disk with a FLAT interior and only a soft outer edge.
     * This is separate from both outline and exterior halo; its center
     * remains uniformly illuminated instead of ring-shaped or hollow.
     * Works for arbitrary circular icon cores using the same Canvas engine.
     */
    fun drawCoreFill(canvas:Canvas,cx:Float,cy:Float,color:Int,fill:GlowCoreFillConfig) {
        if(fill.radiusUnits<=0f || fill.opacity<=0f || Color.alpha(color)==0)return
        val stops=FloatArray(fill.samples+1)
        val colors=IntArray(fill.samples+1)
        for(i in 0..fill.samples) {
            val fraction=i.toFloat()/fill.samples
            stops[i]=fraction
            colors[i]=withAlpha(color,
                GlowCoreFillMath.alphaAt(fraction,fill.opacity,fill.edgeSoftness))
        }
        areaPaint.shader=RadialGradient(cx,cy,fill.radiusUnits,colors,stops,
            Shader.TileMode.CLAMP)
        canvas.drawCircle(cx,cy,fill.radiusUnits,areaPaint)
        areaPaint.shader=null
    }

    /**
     * A smooth radial haze: sampled alpha curve has zero derivative at t=1,
     * and the LAST sample is exactly transparent. No clipped glowing disk.
     */
    fun drawRadial(canvas:Canvas,cx:Float,cy:Float,color:Int,layer:GlowLayerConfig) {
        if(layer.radiusUnits<=0f || layer.opacity<=0f || Color.alpha(color)==0)return
        val steps=layer.samples
        val stops=FloatArray(steps+1)
        val colors=IntArray(steps+1)
        for(i in 0..steps) {
            val fraction=i.toFloat()/steps
            stops[i]=fraction
            colors[i]=withAlpha(color,
                GlowFalloffMath.alphaAt(fraction,layer.opacity,layer.falloff))
        }
        areaPaint.shader=RadialGradient(cx,cy,layer.radiusUnits,colors,stops,
            Shader.TileMode.CLAMP)
        canvas.drawCircle(cx,cy,layer.radiusUnits,areaPaint)
        areaPaint.shader=null
    }
}
