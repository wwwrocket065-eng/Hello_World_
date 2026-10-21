package com.glassstudy.filament

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One passive, touch-transparent technical HUD for all three light controls.
 * The orbit GUIDE is a camera-aware 3D projection; the eight-rayed sun remains
 * a round 2D icon, regardless of camera viewpoint.
 */
class LightIndicatorView(context:Context):View(context) {
    enum class Mode { AZIMUTH,ELEVATION,BRIGHTNESS }
    private var mode:Mode?=null
    private var value=0f
    private var target=0f
    private var visuals:Map<Mode,IndicatorVisualConfig> =
        Mode.values().associateWith { IndicatorVisualConfig.defaults(it) }
    private var tint=Color.rgb(255,220,130)
    private var guide=Color.rgb(173,192,206)
    private var cameraYaw=0.56
    private var cameraPitch=0.34
    private var lightAzimuth=1.3
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND
    }
    private val caption=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign=Paint.Align.CENTER;textSize=16f;style=Paint.Style.FILL
    }
    init {
        isClickable=false;isFocusable=false
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility=GONE;alpha=0f;setLayerType(LAYER_TYPE_SOFTWARE,null)
    }
    /** A single validated overlay schema controls every indicator appearance. */
    fun configureVisuals(next:Map<Mode,IndicatorVisualConfig>) {
        visuals=next.toMap()
        mode?.let { positionView(it) }
        invalidate()
    }
    private fun visual(mode:Mode)=visuals[mode] ?: IndicatorVisualConfig.defaults(mode)

    private fun positionView(next:Mode) {
        val spec=visual(next)
        val density=resources.displayMetrics.density
        // Translate the whole HUD so the 100dp brightness offset cannot clip it.
        translationX=spec.offsetXDp*density
        translationY=spec.offsetYDp*density
    }

    fun show(next:Mode,actual:Float,color:Int,guideColor:Int,
             cameraYawRad:Double,cameraPitchRad:Double,lightAzimuthRad:Double) {
        if(!actual.isFinite() || !cameraYawRad.isFinite() ||
            !cameraPitchRad.isFinite() || !lightAzimuthRad.isFinite())return
        if(mode!=next)value=actual
        mode=next;target=actual;tint=color;guide=guideColor
        cameraYaw=cameraYawRad;cameraPitch=cameraPitchRad;lightAzimuth=lightAzimuthRad
        positionView(next)
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

    private fun glowColor(color:Int,intensity:Float):Int=Color.argb(
        (255f*intensity).roundToInt().coerceIn(0,255),
        Color.red(color),Color.green(color),Color.blue(color))

    /** The sun stays a perfect screen-facing circle; only orbit points project. */
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val kind=mode?:return
        val spec=visual(kind)
        val moving=abs(target-value)>spec.settleThreshold
        value=if(moving)value+(target-value)*spec.smoothing else target
        val saved=canvas.save()
        canvas.translate(width/2f,height/2f)
        val scale=min(width,height).toFloat()/230f
        canvas.scale(scale,scale)

        pen.style=Paint.Style.STROKE
        pen.color=guide
        pen.strokeWidth=spec.guideStrokeUnits
        if(spec.guideGlowUnits>0f && spec.glowOpacity>0f)
            pen.setShadowLayer(spec.guideGlowUnits,0f,0f,glowColor(guide,spec.glowOpacity))
        else pen.clearShadowLayer()

        caption.color=tint;caption.textSize=spec.captionSizeUnits
        var x=0f;var y=0f;var rotation=0f
        var rays=spec.rayLengthMaxUnits
        when(kind) {
            Mode.BRIGHTNESS -> {
                val pct=value.coerceIn(0f,200f)
                y=spec.travelHalfUnits*(1f-2f*pct/200f)
                // Guide stays world-vertical; nothing is drawn through the sun ring.
                val gap=max(spec.guideGapUnits,spec.sunRadiusUnits+spec.guideStrokeUnits)
                if(y-gap > -spec.guideRadiusUnits)
                    canvas.drawLine(0f,-spec.guideRadiusUnits,0f,y-gap,pen)
                if(y+gap < spec.guideRadiusUnits)
                    canvas.drawLine(0f,y+gap,0f,spec.guideRadiusUnits,pen)
                rotation=pct*spec.rotationAtMaxDeg/200f
                rays=spec.rayLengthMinUnits+
                    pct*(spec.rayLengthMaxUnits-spec.rayLengthMinUnits)/200f
                label(canvas,pct.roundToInt().toString()+"%",spec.captionYUnits)
            }
            Mode.AZIMUTH -> {
                val deg=((value%360f)+360f)%360f
                val point=LightGuideProjection.azimuth(Math.toRadians(deg.toDouble()),
                    spec.guideRadiusUnits,cameraYaw,cameraPitch)
                x=point.x;y=point.y
                drawOrbit(canvas,kind,spec,x,y)
                // Do NOT rotate the azimuth/elevation suns themselves.
                label(canvas,(deg.roundToInt()%360).toString()+"°",spec.captionYUnits)
            }
            Mode.ELEVATION -> {
                val deg=value.coerceIn(0f,180f)
                val point=LightGuideProjection.elevation(Math.toRadians(deg.toDouble()),
                    spec.guideRadiusUnits,lightAzimuth,cameraYaw,cameraPitch)
                x=point.x;y=point.y
                drawOrbit(canvas,kind,spec,x,y)
                label(canvas,deg.roundToInt().toString()+"°",spec.captionYUnits)
            }
        }
        pen.clearShadowLayer()

        val sun=canvas.save()
        canvas.translate(x,y)
        if(kind==Mode.BRIGHTNESS)canvas.rotate(rotation)
        pen.style=Paint.Style.STROKE;pen.color=tint;pen.strokeWidth=spec.sunStrokeUnits
        if(spec.sunGlowUnits>0f && spec.glowOpacity>0f)
            pen.setShadowLayer(spec.sunGlowUnits,0f,0f,glowColor(tint,spec.glowOpacity))
        canvas.drawCircle(0f,0f,spec.sunRadiusUnits,pen)
        for(i in 0 until 8) {
            val rad=PI*i/4.0
            canvas.drawLine((spec.rayStartUnits*cos(rad)).toFloat(),
                (spec.rayStartUnits*sin(rad)).toFloat(),
                ((spec.rayStartUnits+rays)*cos(rad)).toFloat(),
                ((spec.rayStartUnits+rays)*sin(rad)).toFloat(),pen)
        }
        pen.clearShadowLayer()
        canvas.restoreToCount(sun)
        canvas.restoreToCount(saved)
        if(moving)postInvalidateOnAnimation()
    }

    /**
     * Sample the projected 3D orbit, rather than stretching the whole canvas.
     * A projected gap around the sun removes BOTH front/back track strokes
     * that would otherwise cross the sun near an edge-on view.
     * No radial spokes or center-reaching lines are rendered.
     */
    private fun drawOrbit(canvas:Canvas,kind:Mode,spec:IndicatorVisualConfig,
                          sunX:Float,sunY:Float) {
        val steps=spec.guideSamples
        val endAngle=if(kind==Mode.AZIMUTH)2*PI else PI
        val gap=max(spec.guideGapUnits,spec.sunRadiusUnits+spec.guideStrokeUnits)
        var previous:LightGuideProjection.Point?=null
        for(i in 0..steps) {
            val theta=endAngle*i.toDouble()/steps
            val point=if(kind==Mode.AZIMUTH)
                LightGuideProjection.azimuth(theta,spec.guideRadiusUnits,cameraYaw,cameraPitch)
            else LightGuideProjection.elevation(theta,spec.guideRadiusUnits,
                lightAzimuth,cameraYaw,cameraPitch)
            val prior=previous
            if(prior!=null && !nearSun(prior,point,sunX,sunY,gap)) {
                canvas.drawLine(prior.x,prior.y,point.x,point.y,pen)
            }
            previous=point
        }
    }
    private fun nearSun(a:LightGuideProjection.Point,b:LightGuideProjection.Point,
                        cx:Float,cy:Float,gap:Float):Boolean {
        val dx=b.x-a.x;val dy=b.y-a.y
        val lengthSquared=dx*dx+dy*dy
        val t=if(lengthSquared<=.00001f)0f else
            (((cx-a.x)*dx+(cy-a.y)*dy)/lengthSquared).coerceIn(0f,1f)
        val px=a.x+t*dx-cx;val py=a.y+t*dy-cy
        return px*px+py*py <= gap*gap
    }
    private fun label(canvas:Canvas,v:String,y:Float) {
        caption.clearShadowLayer()
        canvas.drawText(v,0f,y,caption)
    }
}
