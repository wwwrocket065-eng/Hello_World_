package com.glassstudy.filament

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.DecelerateInterpolator
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
    private var lightElevation=.9
    private var brightnessPercent=100f
    private var captionBoxColor=Color.rgb(17,35,49)
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND
    }
    private val caption=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign=Paint.Align.CENTER;textSize=16f;style=Paint.Style.FILL
    }
    private val captionPlate=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.FILL
    }
    private val glowEngine=CanvasGlowEngine()
    private val ringPath=Path()
    private val rayPath=Path()
    /** Reused buffers: avoid allocating ~180 temporary clipped segments per frame. */
    private val visibleLineParts=FloatArray(8)
    private val fadeInterpolator=DecelerateInterpolator()
    private var fadeOutPending=false
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
             cameraYawRad:Double,cameraPitchRad:Double,lightAzimuthRad:Double,
             lightElevationRad:Double,currentBrightnessPercent:Float,
             captionBackground:Int) {
        if(!actual.isFinite() || !cameraYawRad.isFinite() ||
            !cameraPitchRad.isFinite() || !lightAzimuthRad.isFinite() ||
            !lightElevationRad.isFinite() || !currentBrightnessPercent.isFinite())return
        if(mode!=next)value=actual
        mode=next;target=actual;tint=color;guide=guideColor
        cameraYaw=cameraYawRad;cameraPitch=cameraPitchRad;lightAzimuth=lightAzimuthRad
        lightElevation=lightElevationRad
        brightnessPercent=currentBrightnessPercent.coerceIn(0f,200f)
        captionBoxColor=captionBackground
        positionView(next)
        // Reversing a fade must start from the CURRENT alpha. Resetting alpha
        // to zero here produced a flash on rapid alternating gestures.
        if(visibility!=VISIBLE || fadeOutPending) {
            fadeOutPending=false
            animate().cancel()
            visibility=VISIBLE
            val remaining=(1f-alpha).coerceIn(0f,1f)
            if(remaining<=.001f) alpha=1f
            else animate().alpha(1f)
                .setDuration((visual(next).fadeInMs*remaining).toLong().coerceAtLeast(1))
                .setInterpolator(fadeInterpolator).start()
        }
        invalidate()
    }

    fun hide() {
        if(visibility!=VISIBLE || fadeOutPending || mode==null)return
        val duration=visual(requireNotNull(mode)).fadeOutMs
        fadeOutPending=true
        animate().cancel()
        // KEEP mode during fade-out: onDraw previously returned immediately
        // when mode=null, making the whole orbital guide vanish in one frame.
        if(alpha<=.001f || duration==0L) {
            mode=null;alpha=0f;visibility=GONE;fadeOutPending=false
            return
        }
        animate().alpha(0f)
            .setDuration((duration*alpha.coerceIn(0f,1f)).toLong().coerceAtLeast(1))
            .setInterpolator(fadeInterpolator)
            .withEndAction {
                if(fadeOutPending) {
                    mode=null
                    visibility=GONE
                    fadeOutPending=false
                }
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
        // Azimuth is cyclic: going 359°→1° must advance just 2°, never
        // sweep backwards through the entire circle and jump across the model.
        val delta=if(kind==Mode.AZIMUTH)
            LightDirectionMath.shortestAzimuthDelta(target,value) else target-value
        val moving=abs(delta)>spec.settleThreshold
        value=if(moving)value+delta*spec.smoothing else target
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
        // ALL three suns share the same actual lighting brightness (0–200%).
        val rays=spec.rayLengthMinUnits+
            brightnessPercent*(spec.rayLengthMaxUnits-spec.rayLengthMinUnits)/200f
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
                label(canvas,pct.roundToInt().toString()+"%",spec)
            }
            Mode.AZIMUTH -> {
                val deg=((value%360f)+360f)%360f
                val point=LightGuideProjection.azimuth(Math.toRadians(deg.toDouble()),
                    spec.guideRadiusUnits,cameraYaw,cameraPitch,lightElevation)
                x=point.x;y=point.y
                drawOrbit(canvas,kind,spec,x,y)
                // Do NOT rotate the azimuth/elevation suns themselves.
                label(canvas,(deg.roundToInt()%360).toString()+"°",spec)
            }
            Mode.ELEVATION -> {
                val deg=value.coerceIn(0f,180f)
                val point=LightGuideProjection.elevation(Math.toRadians(deg.toDouble()),
                    spec.guideRadiusUnits,lightAzimuth,cameraYaw,cameraPitch)
                x=point.x;y=point.y
                drawOrbit(canvas,kind,spec,x,y)
                label(canvas,LightDirectionMath.elevationLabel(deg),spec)
            }
        }
        pen.clearShadowLayer()

        val sun=canvas.save()
        canvas.translate(x,y)
        if(kind==Mode.BRIGHTNESS)canvas.rotate(rotation)
        drawSun(canvas,spec,rays)
        canvas.restoreToCount(sun)
        canvas.restoreToCount(saved)
        if(moving)postInvalidateOnAnimation()
    }


    /**
     * One glow engine and three independently patchable layers.
     * The ring gets the strongest feathered stroke, the rays a milder stroke,
     * and the outside a smooth radial haze. No hard edge or opaque glow disk.
     * The actual sun circle and rays remain crisp, round and independent
     * of perspective projection; only brightness mode rotates the glyph.
     */
    private fun drawSun(canvas:Canvas,spec:IndicatorVisualConfig,rays:Float) {
        ringPath.rewind()
        ringPath.addCircle(0f,0f,spec.sunRadiusUnits,Path.Direction.CW)
        rayPath.rewind()
        for(i in 0 until 8) {
            val rad=PI*i/4.0
            rayPath.moveTo((spec.rayStartUnits*cos(rad)).toFloat(),
                (spec.rayStartUnits*sin(rad)).toFloat())
            rayPath.lineTo(((spec.rayStartUnits+rays)*cos(rad)).toFloat(),
                ((spec.rayStartUnits+rays)*sin(rad)).toFloat())
        }

        val glow=spec.glowEffect
        glowEngine.drawRadial(canvas,0f,0f,tint,glow.ambient)
        // Draw the uniform illuminated INNER DISK under the opaque ring.
        // Its brightness is independent of both the ring and eight ray glows.
        glowEngine.drawCoreFill(canvas,0f,0f,tint,glow.coreFill)
        glowEngine.drawStroke(canvas,rayPath,tint,spec.sunStrokeUnits,glow.secondaryStroke)
        glowEngine.drawStroke(canvas,ringPath,tint,spec.sunStrokeUnits,glow.primaryStroke)

        pen.clearShadowLayer()
        pen.style=Paint.Style.STROKE
        pen.strokeWidth=spec.sunStrokeUnits
        pen.color=tint
        drawSunGlyph(canvas)
    }

    /** Shared eight-ray geometry for the three indicator modes. */
    private fun drawSunGlyph(canvas:Canvas) {
        canvas.drawPath(ringPath,pen)
        canvas.drawPath(rayPath,pen)
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
                // Fixed guide and marker share the same XZ orbit; the marker
                // flips hemisphere after 90° without leaving the circle.
                LightGuideProjection.azimuthOrbit(theta,spec.guideRadiusUnits,
                    cameraYaw,cameraPitch)
            else LightGuideProjection.elevation(theta,spec.guideRadiusUnits,
                lightAzimuth,cameraYaw,cameraPitch)
            val prior=previous
            if(prior!=null) {
                // Exact continuous intersection instead of dropping entire
                // sampled segments. This makes lines emerge smoothly from
                // behind the sun as the user drags, like the brightness bar.
                val count=GuideGapClipMath.visibleSegments(
                    prior.x,prior.y,point.x,point.y,
                    sunX,sunY,gap,visibleLineParts)
                for(segment in 0 until count) {
                    val offset=segment*4
                    canvas.drawLine(visibleLineParts[offset],visibleLineParts[offset+1],
                        visibleLineParts[offset+2],visibleLineParts[offset+3],pen)
                }
            }
            previous=point
        }
    }
    private fun label(canvas:Canvas,v:String,spec:IndicatorVisualConfig) {
        caption.clearShadowLayer()
        // Readable dark rounded chip behind degrees and percentages. Geometry,
        // tint and opacity are all validated signed .pupdate resources.
        val y=spec.captionYUnits
        val textWidth=caption.measureText(v)
        val metrics=caption.fontMetrics
        val left=-textWidth/2f-spec.captionBoxPaddingXUnits
        val right=textWidth/2f+spec.captionBoxPaddingXUnits
        val top=y+metrics.ascent-spec.captionBoxPaddingYUnits
        val bottom=y+metrics.descent+spec.captionBoxPaddingYUnits
        val alpha=(Color.alpha(captionBoxColor)*spec.captionBoxOpacity)
            .roundToInt().coerceIn(0,255)
        captionPlate.color=Color.argb(alpha,Color.red(captionBoxColor),
            Color.green(captionBoxColor),Color.blue(captionBoxColor))
        canvas.drawRoundRect(left,top,right,bottom,
            spec.captionBoxCornerUnits,spec.captionBoxCornerUnits,captionPlate)
        // Label color is always fully opaque; captionBoxOpacity affects the
        // plate ONLY, never the degrees, percent sign or numeric glyphs.
        caption.color=Color.rgb(Color.red(tint),Color.green(tint),Color.blue(tint))
        caption.alpha=255
        canvas.drawText(v,0f,y,caption)
    }
}
