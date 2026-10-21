package com.glassstudy.filament

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One passive, touch-transparent HUD for all three Filament light parameters. */
class LightIndicatorView(context:Context):View(context) {
    enum class Mode { AZIMUTH,ELEVATION,BRIGHTNESS }
    private var mode:Mode?=null
    private var value=0f
    private var target=0f
    private var visuals:Map<Mode,IndicatorVisualConfig> =
        Mode.values().associateWith { IndicatorVisualConfig.defaults(it) }
    private var style="classic"
    private var tint=Color.rgb(242,214,158)
    private var guide=Color.rgb(173,192,206)
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND
    }
    private val caption=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign=Paint.Align.CENTER;textSize=16f;style=Paint.Style.FILL
    }
    init {
        isClickable=false;isFocusable=false
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility=GONE;alpha=0f;setLayerType(LAYER_TYPE_SOFTWARE,null)
    }
    /** Patch-defined numeric appearance shared by every indicator mode. */
    fun configureVisuals(next:Map<Mode,IndicatorVisualConfig>) {
        visuals=next.toMap()
        invalidate()
    }
    private fun visual(mode:Mode)=visuals[mode] ?: IndicatorVisualConfig.defaults(mode)
    fun show(next:Mode,actual:Float,appearance:String,color:Int,guideColor:Int) {
        if(!actual.isFinite())return
        if(mode!=next)value=actual
        mode=next;target=actual;style=appearance;tint=color;guide=guideColor
        if(visibility!=VISIBLE) {
            animate().cancel();alpha=0f;visibility=VISIBLE
            animate().alpha(1f).setDuration(visual(next).fadeInMs).start()
        }
        invalidate()
    }
    fun hide() {
        if(visibility==GONE)return
        val duration=mode?.let { visual(it).fadeOutMs } ?: 160L
        mode=null;animate().cancel()
        animate().alpha(0f).setDuration(duration).withEndAction {
            if(mode==null)visibility=GONE
        }.start()
    }
    override fun onTouchEvent(event:android.view.MotionEvent?):Boolean=false
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val kind=mode?:return
        val spec=visual(kind)
        val moving=abs(target-value)>spec.settleThreshold
        value=if(moving)value+(target-value)*spec.smoothing else target
        val save=canvas.save()
        canvas.translate(width/2f,height/2f)
        val scale=min(width,height).toFloat()/230f
        canvas.scale(scale,scale)
        // Offsets are physical dp; the indicator drawing is otherwise in 230-unit space.
        canvas.translate(spec.offsetXDp*resources.displayMetrics.density/scale.coerceAtLeast(.01f),
            spec.offsetYDp*resources.displayMetrics.density/scale.coerceAtLeast(.01f))
        pen.style=Paint.Style.STROKE;pen.color=guide
        pen.strokeWidth=if(style=="minimal")spec.guideStrokeUnits*.61f else spec.guideStrokeUnits
        caption.color=tint;caption.textSize=spec.captionSizeUnits
        var x=0f;var y=0f;var rotation=0f;var rays=spec.rayLengthMinUnits
        when(kind) {
            Mode.BRIGHTNESS -> {
                val pct=value.coerceIn(0f,200f)
                y=spec.travelHalfUnits*(1f-2f*pct/200f)
                if(y-spec.guideGapUnits > -spec.guideRadiusUnits)
                    canvas.drawLine(0f,-spec.guideRadiusUnits,0f,y-spec.guideGapUnits,pen)
                if(y+spec.guideGapUnits < spec.guideRadiusUnits)
                    canvas.drawLine(0f,y+spec.guideGapUnits,0f,spec.guideRadiusUnits,pen)
                rotation=pct*spec.rotationAtMaxDeg/200f
                rays=spec.rayLengthMinUnits+
                    pct*(spec.rayLengthMaxUnits-spec.rayLengthMinUnits)/200f
                label(canvas,pct.roundToInt().toString()+"%",spec.captionYUnits)
            }
            Mode.ELEVATION -> {
                val deg=value.coerceIn(0f,180f)
                canvas.drawArc(RectF(-spec.guideRadiusUnits,-spec.guideRadiusUnits,
                    spec.guideRadiusUnits,spec.guideRadiusUnits),180f,180f,false,pen)
                val rad=Math.toRadians(deg.toDouble())
                x=(spec.guideRadiusUnits*cos(rad)).toFloat();y=(-spec.guideRadiusUnits*sin(rad)).toFloat()
                rotation=deg*spec.rotationAtMaxDeg/180f
                canvas.drawLine(0f,0f,x*.78f,y*.78f,pen)
                label(canvas,deg.roundToInt().toString()+"°",spec.captionYUnits)
            }
            Mode.AZIMUTH -> {
                val deg=((value%360f)+360f)%360f
                canvas.drawCircle(0f,-4f,spec.guideRadiusUnits,pen)
                val rad=Math.toRadians(deg.toDouble())
                x=(spec.guideRadiusUnits*sin(rad)).toFloat();y=(-4-spec.guideRadiusUnits*cos(rad)).toFloat()
                rotation=deg*spec.rotationAtMaxDeg/360f
                canvas.drawLine(0f,-4f,x*.78f,-4f+(y+4f)*.78f,pen)
                label(canvas,(deg.roundToInt()%360).toString()+"°",spec.captionYUnits)
            }
        }
        if(style!="minimal") {
            pen.style=Paint.Style.FILL;pen.color=Color.argb(80,0,0,0)
            canvas.drawOval(x-16f,y+12f,x+16f,y+18f,pen)
        }
        val sun=canvas.save()
        canvas.translate(x,y);canvas.rotate(rotation)
        pen.style=Paint.Style.STROKE;pen.color=tint
        pen.strokeWidth=if(style=="minimal")spec.sunStrokeUnits*.5833f else spec.sunStrokeUnits
        if(style=="technical")pen.setShadowLayer(7f,0f,0f,tint)
        canvas.drawCircle(0f,0f,spec.sunRadiusUnits,pen)
        for(i in 0 until 8) {
            val rad=PI*i/4.0
            canvas.drawLine((spec.rayStartUnits*cos(rad)).toFloat(),
                (spec.rayStartUnits*sin(rad)).toFloat(),
                ((spec.rayStartUnits+rays)*cos(rad)).toFloat(),
                ((spec.rayStartUnits+rays)*sin(rad)).toFloat(),pen)
        }
        pen.clearShadowLayer()
        canvas.restoreToCount(sun);canvas.restoreToCount(save)
        if(moving)postInvalidateOnAnimation()
    }
    private fun label(canvas:Canvas,v:String,y:Float) {
        caption.setShadowLayer(2f,0f,2f,Color.BLACK)
        canvas.drawText(v,0f,y,caption)
        caption.clearShadowLayer()
    }
}
