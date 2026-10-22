package com.glassstudy.filament

import kotlin.math.sqrt

/**
 * Smoothly cut a moving round sun halo out of a polyline guide.
 *
 * A whole-segment Boolean exclusion makes gaps visibly jump by one guide
 * sample (~2 degrees). Analytic intersections make the guide endpoints
 * slide continuously below the marker, just like the brightness ruler.
 *
 * The caller owns/reuses [out] (>=8 floats). No per-segment allocations.
 * Returns 0, 1, or 2 visible segments, each (x0,y0,x1,y1).
 */
object GuideGapClipMath {
    fun visibleSegments(ax:Float,ay:Float,bx:Float,by:Float,
                        cx:Float,cy:Float,radius:Float,out:FloatArray):Int {
        require(out.size>=8 && radius>=0f)
        val dx=bx-ax
        val dy=by-ay
        val a=dx*dx+dy*dy
        if(a<=1e-10f)return 0

        fun write(slot:Int,t0:Float,t1:Float) {
            val o=slot*4
            out[o]=ax+t0*dx;out[o+1]=ay+t0*dy
            out[o+2]=ax+t1*dx;out[o+3]=ay+t1*dy
        }
        val px=ax-cx
        val py=ay-cy
        val b=2f*(px*dx+py*dy)
        val c=px*px+py*py-radius*radius
        val discriminant=b*b-4f*a*c
        // If the infinite line misses the circle, its entire segment is
        // visible. No nearSun Boolean step or gap quantization.
        if(discriminant<=0f) {
            if(c<0f)return 0
            write(0,0f,1f)
            return 1
        }
        val root=sqrt(discriminant.toDouble()).toFloat()
        val first=(-b-root)/(2f*a)
        val last=(-b+root)/(2f*a)
        if(first>=1f || last<=0f) {
            write(0,0f,1f)
            return 1
        }
        var count=0
        if(first>0f) {
            write(count,0f,first.coerceAtMost(1f))
            count++
        }
        if(last<1f) {
            write(count,last.coerceAtLeast(0f),1f)
            count++
        }
        return count
    }
}
