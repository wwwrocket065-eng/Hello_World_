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
    private var brightnessOffsetXDp=40f
    private var brightnessRotationAt200Deg=90f
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
    /** Called with a validated signed overlay configuration; only BRIGHTNESS moves right. */
    fun configureBrightness(offsetXDp:Float,rotationAt200Deg:Float) {
        brightnessOffsetXDp=offsetXDp
        brightnessRotationAt200Deg=rotationAt200Deg
        invalidate()
    }
    fun show(next:Mode,actual:Float,appearance:String,color:Int,guideColor:Int) {
        if(!actual.isFinite())return
        if(mode!=next)value=actual
        mode=next;target=actual;style=appearance;tint=color;guide=guideColor
        if(visibility!=VISIBLE) {
            animate().cancel();alpha=0f;visibility=VISIBLE
            animate().alpha(1f).setDuration(140L).start()
        }
        invalidate()
    }
    fun hide() {
        if(visibility==GONE)return
        mode=null;animate().cancel()
        animate().alpha(0f).setDuration(160L).withEndAction {
            if(mode==null)visibility=GONE
        }.start()
    }
    override fun onTouchEvent(event:android.view.MotionEvent?):Boolean=false
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val kind=mode?:return
        val moving=abs(target-value)>.08f
        value=if(moving)value+(target-value)*.28f else target
        val save=canvas.save()
        canvas.translate(width/2f,height/2f)
        val scale=min(width,height).toFloat()/230f
        canvas.scale(scale,scale)
        if(kind==Mode.BRIGHTNESS) {
            // Measure displacement in physical dp even when indicatorSizeDp changes.
            canvas.translate(brightnessOffsetXDp*resources.displayMetrics.density/
                scale.coerceAtLeast(.01f),0f)
        }
        pen.style=Paint.Style.STROKE;pen.color=guide
        pen.strokeWidth=if(style=="minimal")1.4f else 2.3f
        caption.color=tint
        var x=0f;var y=0f;var rotation=0f;var rays=6f
        when(kind) {
            Mode.BRIGHTNESS -> {
                val pct=value.coerceIn(0f,200f)
                y=63f-126f*pct/200f
                if(y-24f> -69f)canvas.drawLine(0f,-69f,0f,y-24f,pen)
                if(y+24f<69f)canvas.drawLine(0f,y+24f,0f,69f,pen)
                rotation=pct*brightnessRotationAt200Deg/200f;rays=3f+pct*9f/200f
                label(canvas,pct.roundToInt().toString()+"%")
            }
            Mode.ELEVATION -> {
                val deg=value.coerceIn(0f,180f)
                canvas.drawArc(RectF(-71f,-71f,71f,71f),180f,180f,false,pen)
                val rad=Math.toRadians(deg.toDouble())
                x=(71*cos(rad)).toFloat();y=(-71*sin(rad)).toFloat()
                canvas.drawLine(0f,0f,x*.78f,y*.78f,pen)
                label(canvas,deg.roundToInt().toString()+"°")
            }
            Mode.AZIMUTH -> {
                val deg=((value%360f)+360f)%360f
                canvas.drawCircle(0f,-4f,71f,pen)
                val rad=Math.toRadians(deg.toDouble())
                x=(71*sin(rad)).toFloat();y=(-4-71*cos(rad)).toFloat()
                canvas.drawLine(0f,-4f,x*.78f,-4f+(y+4f)*.78f,pen)
                label(canvas,(deg.roundToInt()%360).toString()+"°")
            }
        }
        if(style!="minimal") {
            pen.style=Paint.Style.FILL;pen.color=Color.argb(80,0,0,0)
            canvas.drawOval(x-16f,y+12f,x+16f,y+18f,pen)
        }
        val sun=canvas.save()
        canvas.translate(x,y);canvas.rotate(rotation)
        pen.style=Paint.Style.STROKE;pen.color=tint
        pen.strokeWidth=if(style=="minimal")1.4f else 2.4f
        if(style=="technical")pen.setShadowLayer(7f,0f,0f,tint)
        canvas.drawCircle(0f,0f,9f,pen)
        for(i in 0 until 8) {
            val rad=PI*i/4.0
            canvas.drawLine((14*cos(rad)).toFloat(),(14*sin(rad)).toFloat(),
                ((14+rays)*cos(rad)).toFloat(),((14+rays)*sin(rad)).toFloat(),pen)
        }
        pen.clearShadowLayer()
        canvas.restoreToCount(sun);canvas.restoreToCount(save)
        if(moving)postInvalidateOnAnimation()
    }
    private fun label(canvas:Canvas,v:String) {
        caption.setShadowLayer(2f,0f,2f,Color.BLACK)
        canvas.drawText(v,0f,97f,caption)
        caption.clearShadowLayer()
    }
}
