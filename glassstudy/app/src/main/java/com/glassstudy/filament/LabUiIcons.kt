package com.glassstudy.filament

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * No external bitmap / font-dependent glyphs: chip, sun, menu and other symbols
 * are actually drawn and centered in Android Canvas. UI patches may select only
 * the supported names in UiLayout.kt.
 */
class LabIconView(context:Context, val iconName:String):View(context) {
    var iconColor:Int=Color.rgb(109,224,222)
        set(value) { field=value;invalidate() }
    var glyphScale:Float=.82f
        set(value) { field=value.coerceIn(.55f,1f);invalidate() }
    var chipCenterFilled:Boolean=true
        set(value) { field=value;invalidate() }
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE;strokeJoin=Paint.Join.ROUND
        strokeCap=Paint.Cap.ROUND;strokeWidth=2.2f
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val dimension=min(width,height).toFloat()
        if(dimension<=0f)return
        val save=canvas.save()
        canvas.translate(width/2f,height/2f)
        canvas.scale(dimension/44f*glyphScale,dimension/44f*glyphScale)
        ink.color=iconColor
        ink.style=Paint.Style.STROKE
        ink.strokeWidth=2.15f
        when(iconName) {
            "chip" -> {
                // A square with eight+ contacts and a centered inner square.
                canvas.drawRoundRect(-9f,-9f,9f,9f,1.5f,1.5f,ink)
                val previous=ink.style
                ink.style=if(chipCenterFilled)Paint.Style.FILL else Paint.Style.STROKE
                canvas.drawRect(-4f,-4f,4f,4f,ink)
                ink.style=previous
                for(p in listOf(-6f,0f,6f)) {
                    canvas.drawLine(p,-14f,p,-9f,ink)
                    canvas.drawLine(p,9f,p,14f,ink)
                    canvas.drawLine(-14f,p,-9f,p,ink)
                    canvas.drawLine(9f,p,14f,p,ink)
                }
            }
            "sun" -> {
                canvas.drawCircle(0f,0f,6f,ink)
                for(i in 0 until 8) {
                    val rad=Math.PI*i/4.0
                    canvas.drawLine((10*cos(rad)).toFloat(),(10*sin(rad)).toFloat(),
                        (15*cos(rad)).toFloat(),(15*sin(rad)).toFloat(),ink)
                }
            }
            "reset" -> {
                canvas.drawArc(-11f,-11f,11f,11f,-64f,292f,false,ink)
                val arrow=Path().apply {
                    moveTo(-13f,-8f);lineTo(-13f,-1f);lineTo(-6f,-1f)
                }
                canvas.drawPath(arrow,ink)
            }
            "menu" -> {
                for(y in listOf(-9f,0f,9f)){
                    canvas.drawLine(-6f,y,13f,y,ink)
                    val saved=ink.style
                    ink.style=Paint.Style.FILL
                    canvas.drawCircle(-12f,y,1.7f,ink)
                    ink.style=saved
                }
            }
            "objects" -> {
                canvas.drawRect(-10f,-10f,10f,10f,ink)
                canvas.drawLine(-10f,-10f,0f,-15f,ink)
                canvas.drawLine(10f,-10f,0f,-15f,ink)
                canvas.drawLine(0f,-15f,0f,5f,ink)
                canvas.drawLine(-10f,10f,0f,5f,ink)
                canvas.drawLine(10f,10f,0f,5f,ink)
            }
            "grid" -> {
                canvas.drawRect(-12f,-12f,12f,12f,ink)
                for(v in listOf(-4f,4f)){
                    canvas.drawLine(v,-12f,v,12f,ink)
                    canvas.drawLine(-12f,v,12f,v,ink)
                }
            }
            "quality" -> {
                canvas.drawRoundRect(-12f,-11f,12f,11f,1.5f,1.5f,ink)
                canvas.drawLine(-6f,-4f,6f,-4f,ink)
                canvas.drawLine(-6f,3f,6f,3f,ink)
            }
            "fps" -> {
                canvas.drawArc(-12f,-12f,12f,12f,200f,140f,false,ink)
                canvas.drawLine(0f,0f,7f,-5f,ink)
                canvas.drawCircle(0f,0f,2f,ink)
            }
            "info" -> {
                canvas.drawCircle(0f,0f,12f,ink)
                canvas.drawLine(0f,-1f,0f,7f,ink)
                val saved=ink.style
                ink.style=Paint.Style.FILL
                canvas.drawCircle(0f,-6f,1.4f,ink)
                ink.style=saved
            }
        }
        canvas.restoreToCount(save)
    }
}

/** Restored identity: outlined rhombus, an inner cyan rhombus and long lower stroke. */
class LabLogoView(context:Context):View(context) {
    var showUnderline:Boolean=false
        set(value){field=value;invalidate()}
    var logoColor:Int=Color.rgb(109,224,222)
        set(value){field=value;invalidate()}
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.rgb(109,224,222)
        style=Paint.Style.STROKE
        strokeWidth=2.2f
        strokeJoin=Paint.Join.ROUND
        strokeCap=Paint.Cap.ROUND
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        pen.color=logoColor
        val save=canvas.save()
        canvas.scale(width/40f,height/48f)
        val outside=Path().apply {
            moveTo(20f,7f);lineTo(35f,22f)
            lineTo(20f,37f);lineTo(5f,22f);close()
        }
        canvas.drawPath(outside,pen)
        val inside=Path().apply {
            moveTo(20f,15.5f);lineTo(26.5f,22f)
            lineTo(20f,28.5f);lineTo(13.5f,22f);close()
        }
        pen.style=Paint.Style.FILL
        canvas.drawPath(inside,pen)
        pen.style=Paint.Style.STROKE
        // Extended underline on the bottom-left of the logo.
        if(showUnderline) canvas.drawLine(2f,42f,26f,42f,pen)
        canvas.restoreToCount(save)
    }
}
