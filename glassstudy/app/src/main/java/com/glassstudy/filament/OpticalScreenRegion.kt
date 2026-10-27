package com.glassstudy.filament

import kotlin.math.*

/** Conservative screen rectangle; only its pixels need dielectric path tracing. */
object OpticalScreenRegion {
    data class Rect(val x:Int,val y:Int,val width:Int,val height:Int)
    fun project(contour:FloatArray,yaw:Double,pitch:Double,distance:Double,
                fov:Double,aspect:Double,width:Int,height:Int):Rect {
        val full=Rect(0,0,width,height)
        if(width<=0 || height<=0 || contour.size<4)return full
        var radius=0.0;var low=Double.POSITIVE_INFINITY;var high=Double.NEGATIVE_INFINITY
        for(j in contour.indices step 2) {
            radius=max(radius,contour[j+1].toDouble())
            low=min(low,contour[j].toDouble());high=max(high,contour[j].toDouble())
        }
        val ex=distance*cos(pitch)*sin(yaw)
        val ey=1+distance*sin(pitch)
        val ez=distance*cos(pitch)*cos(yaw)
        // Same orbit camera and target (0,1,0) as both rendering passes.
        val fx=-cos(pitch)*sin(yaw);val fy=-sin(pitch);val fz=-cos(pitch)*cos(yaw)
        val rx=cos(yaw);val rz=-sin(yaw)
        val ux=-sin(pitch)*sin(yaw);val uy=cos(pitch);val uz=-sin(pitch)*cos(yaw)
        val tangent=tan(Math.toRadians(fov)*.5)
        var left=Double.POSITIVE_INFINITY;var right=Double.NEGATIVE_INFINITY
        var bottom=Double.POSITIVE_INFINITY;var top=Double.NEGATIVE_INFINITY
        for(x in doubleArrayOf(-radius,radius))for(y in doubleArrayOf(low,high))
            for(z in doubleArrayOf(-radius,radius)) {
                val dx=x-ex;val dy=y-ey;val dz=z-ez
                val depth=dx*fx+dy*fy+dz*fz
                // If the bound crosses the eye plane, never accidentally clip glass.
                if(depth<=.0001)return full
                val px=((dx*rx+dz*rz)/(depth*tangent*aspect)*.5+.5)*width
                val py=((dx*ux+dy*uy+dz*uz)/(depth*tangent)*.5+.5)*height
                left=min(left,px);right=max(right,px);bottom=min(bottom,py);top=max(top,py)
            }
        // Two pixels cover jitter and the boundary of the projected bound.
        val x0=(floor(left).toInt()-2).coerceIn(0,width)
        val x1=(ceil(right).toInt()+2).coerceIn(0,width)
        val y0=(floor(bottom).toInt()-2).coerceIn(0,height)
        val y1=(ceil(top).toInt()+2).coerceIn(0,height)
        return Rect(x0,y0,max(0,x1-x0),max(0,y1-y0))
    }
}
