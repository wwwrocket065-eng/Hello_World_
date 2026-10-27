package com.glassstudy.filament

import org.json.JSONObject
import kotlin.math.*

/**
 * CPU photon projection through the actual signed goblet meridian.
 *
 * Ray/cone and horizontal annulus intersections follow the same Catmull-Rom
 * lathe profile as ParametricGoblet, including outer/inner walls, foot and stem.
 * Snell's law + Fresnel for successive air/glass interfaces; photon splats
 * build signed change to direct irradiance on the floor (dark AND bright).
 * It is calculated on an executor only when scene inputs settle, NEVER/frame.
 *
 * Finite resolution, single representative transmitted ray, no multi-bounce
 * reflection caustics or spectral dispersion: honest mobile approximation.
 */
object GlassRayTrace {
    data class Field(val size:Int,val halfWidth:Double,val flux:DoubleArray,
                     val hits:Int,val refracted:Int)
    private data class Ring(val y:Double,val r:Double)
    private data class Vec(val x:Double,val y:Double,val z:Double) {
        operator fun plus(o:Vec)=Vec(x+o.x,y+o.y,z+o.z)
        operator fun times(t:Double)=Vec(x*t,y*t,z*t)
        fun dot(o:Vec)=x*o.x+y*o.y+z*o.z
        fun norm():Vec {val l=sqrt(dot(this)).coerceAtLeast(1e-12);return this*(1.0/l)}
    }
    private data class Segment(val a:Ring,val b:Ring)
    private data class Hit(val t:Double,val normal:Vec)
    private const val FLOOR=-.06
    private const val IOR=1.52
    private const val EPS=1e-4
    private const val SIZE=104
    private const val SUBSAMPLES=2

    private fun rings(raw:ByteArray):List<Ring> {
        val array=JSONObject(String(raw,Charsets.UTF_8)).getJSONArray("profile")
        require(array.length() in 24..80)
        val source=(0 until array.length()).map {
            val p=array.getJSONArray(it)
            Ring(p.getDouble(0),p.getDouble(1))
        }
        fun cubic(a:Double,b:Double,c:Double,d:Double,t:Double):Double =
            .5*(2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+
                (-a+3*b-3*c+d)*t*t*t)
        val sampled=ArrayList<Ring>(source.size*2)
        for(j in 0 until source.lastIndex) for(k in 0..1) {
            val a=source[max(0,j-1)];val b=source[j]
            val c=source[j+1];val d=source[min(j+2,source.lastIndex)]
            val t=k/2.0
            sampled.add(Ring(cubic(a.y,b.y,c.y,d.y,t),
                max(.001,cubic(a.r,b.r,c.r,d.r,t))))
        }
        sampled.add(source.last())
        return sampled
    }

    private fun intersection(p:Vec,d:Vec,segments:List<Segment>):Hit? {
        var closest=Double.POSITIVE_INFINITY
        var normal:Vec?=null
        for(segment in segments) {
            val a=segment.a;val b=segment.b
            val dy=b.y-a.y;val dr=b.r-a.r
            if(abs(dy)<1e-8) {
                if(abs(d.y)<1e-8)continue
                val t=(a.y-p.y)/d.y
                if(t<=EPS || t>=closest)continue
                val x=p.x+t*d.x;val z=p.z+t*d.z
                val radius=hypot(x,z)
                if(radius+1e-5<min(a.r,b.r) ||
                    radius-1e-5>max(a.r,b.r))continue
                closest=t
                normal=Vec(0.0,if(dr>0.0)-1.0 else 1.0,0.0)
                continue
            }
            val slope=dr/dy
            val r0=a.r+slope*(p.y-a.y)
            val k=slope*d.y
            val aa=d.x*d.x+d.z*d.z-k*k
            val bb=2.0*(p.x*d.x+p.z*d.z-r0*k)
            val cc=p.x*p.x+p.z*p.z-r0*r0
            val roots=when {
                abs(aa)<1e-12 -> if(abs(bb)>1e-12) doubleArrayOf(-cc/bb) else doubleArrayOf()
                else -> {
                    val disc=bb*bb-4*aa*cc
                    if(disc<0.0)doubleArrayOf() else {
                        val h=sqrt(disc)
                        doubleArrayOf((-bb-h)/(2*aa),(-bb+h)/(2*aa))
                    }
                }
            }
            for(t in roots) {
                if(t<=EPS || t>=closest)continue
                val y=p.y+t*d.y
                if(y<min(a.y,b.y)-1e-5 || y>max(a.y,b.y)+1e-5)continue
                val x=p.x+t*d.x;val z=p.z+t*d.z
                val rr=hypot(x,z).coerceAtLeast(1e-9)
                closest=t
                normal=Vec(dy*x/rr,-dr,dy*z/rr).norm()
            }
        }
        return if(normal!=null)Hit(closest,normal) else null
    }

    /** Return an irradiance ratio map with untouched floor = exactly 1.0. */
    fun trace(profile:ByteArray,azimuth:Double,elevation:Double,brightness:Double):Field? {
        require(azimuth.isFinite() && elevation.isFinite() && elevation in 0.0..PI)
        require(brightness.isFinite() && brightness in 0.0..2.0)
        val sun=LightDirectionMath.source(azimuth,elevation)
        if(brightness<.015 || sun.y<.27f)return null
        val direction=Vec(-sun.x.toDouble(),-sun.y.toDouble(),-sun.z.toDouble()).norm()
        val halfWidth=(2.25+2.1*hypot(direction.x,direction.z)/(-direction.y))
            .coerceIn(2.7,5.45)
        val profileRings=rings(profile)
        val segments=profileRings.zipWithNext().map { Segment(it.first,it.second) }
        val difference=DoubleArray(SIZE*SIZE)
        val step=2.0*halfWidth/SIZE
        val incoming=1.0/(SUBSAMPLES*SUBSAMPLES)
        val floorT=(4.6-FLOOR)/(-direction.y)
        var hitCount=0;var passed=0
        for(z in 0 until SIZE)for(x in 0 until SIZE) {
            for(zi in 0 until SUBSAMPLES)for(xi in 0 until SUBSAMPLES) {
                val floorX=-halfWidth+(x+(xi+.5)/SUBSAMPLES)*step
                val floorZ=-halfWidth+(z+(zi+.5)/SUBSAMPLES)*step
                var p=Vec(floorX-direction.x*floorT,4.6,
                    floorZ-direction.z*floorT)
                var d=direction
                var energy=1.0
                var count=0
                for(bounce in 0 until 8) {
                    val hit=intersection(p,d,segments) ?: break
                    count++
                    p=p+d*hit.t
                    val entering=d.dot(hit.normal)<0.0
                    val n=if(entering)hit.normal else hit.normal*(-1.0)
                    val eta=if(entering)1.0/IOR else IOR
                    val cosI=(-d.dot(n)).coerceIn(0.0,1.0)
                    val sine2=eta*eta*(1.0-cosI*cosI)
                    if(sine2>=1.0) {
                        // Total internal reflection: follow the reflected ray.
                        d=(d+n*(2.0*cosI)).norm()
                        p=p+d*EPS*2.0
                        continue
                    }
                    val cosT=sqrt(1.0-sine2)
                    val fresnel0=((IOR-1.0)/(IOR+1.0)).pow(2)
                    val fresnel=fresnel0+(1.0-fresnel0)*(1.0-cosI).pow(5)
                    energy*=(1.0-fresnel)
                    d=(d*eta+n*(eta*cosI-cosT)).norm()
                    p=p+d*EPS*2.0
                    if(energy<.012)break
                }
                if(count==0)continue
                hitCount++
                // Every blocked incident photon creates a corresponding deficit.
                difference[z*SIZE+x]-=incoming
                if(d.y>=-1e-6)continue
                val t=(FLOOR-p.y)/d.y
                if(t<=0.0)continue
                val impactX=p.x+d.x*t
                val impactZ=p.z+d.z*t
                val gx=(impactX+halfWidth)/step-.5
                val gz=(impactZ+halfWidth)/step-.5
                if(!gx.isFinite() || !gz.isFinite())continue
                val ix=floor(gx).toInt();val iz=floor(gz).toInt()
                val fx=gx-ix;val fz=gz-iz
                for(dz in 0..1)for(dx in 0..1) {
                    val cx=ix+dx;val cz=iz+dz
                    if(cx !in 0 until SIZE || cz !in 0 until SIZE)continue
                    val weight=(if(dx==0)1.0-fx else fx)*
                        (if(dz==0)1.0-fz else fz)
                    difference[cz*SIZE+cx]+=incoming*energy*weight
                }
                passed++
            }
        }
        // Finite solar angular size and pixel sampling blur only the RECEIVER,
        // not the geometry. Preserve integral of the photon map.
        val smooth=DoubleArray(difference.size)
        for(z in 0 until SIZE)for(x in 0 until SIZE) {
            var sum=0.0;var weights=0.0
            for(dz in -1..1)for(dx in -1..1) {
                val cx=x+dx;val cz=z+dz
                if(cx !in 0 until SIZE || cz !in 0 until SIZE)continue
                val w=(if(dx==0)2.0 else 1.0)*(if(dz==0)2.0 else 1.0)
                sum+=w*difference[cz*SIZE+cx];weights+=w
            }
            smooth[z*SIZE+x]=1.0+sum/weights
        }
        return Field(SIZE,halfWidth,smooth,hitCount,passed)
    }
}
