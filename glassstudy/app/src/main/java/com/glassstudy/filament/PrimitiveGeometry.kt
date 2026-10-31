package com.glassstudy.filament

import kotlin.math.*

/** Closed solid calibration objects; one shape description for mesh and ray pass. */
object PrimitiveGeometry {
    val ids=listOf("sphere","cone","cube","cylinder")
    fun type(id:String)=ids.indexOf(id)+1
    data class Mesh(val positions:FloatArray,val normals:FloatArray,val indices:IntArray,val edges:List<Pair<Int,Int>>)
    fun contour(id:String,r:Float,h:Float):FloatArray {
        require(id in ids)
        // Cube uses a conservative circumscribed cylinder only for work bounds.
        val bound=if(id=="cube")r*sqrt(2f) else r
        return floatArrayOf(0f,0f,0f,bound,h,bound,h,0f)
    }
    fun mesh(id:String,quality:String,adaptivity:Int,r:Float,h:Float):Mesh {
        require(id in ids && adaptivity in 0..100 && r>0 && h>0)
        val sides=when(quality){"low"->14;"medium"->28;"high"->56;"ultra"->112;else->error("Quality")}
        val p=ArrayList<Float>();val n=ArrayList<Float>();val triangles=ArrayList<Int>()
        val edges=linkedSetOf<Pair<Int,Int>>()
        fun vertex(x:Float,y:Float,z:Float,nx:Float,ny:Float,nz:Float):Int {
            val index=p.size/3;p.addAll(listOf(x,y,z));n.addAll(listOf(nx,ny,nz));return index
        }
        fun edge(a:Int,b:Int){edges.add(min(a,b) to max(a,b))}
        fun tri(a:Int,b:Int,c:Int){triangles.addAll(listOf(a,b,c))}
        fun quad(a:Int,b:Int,c:Int,d:Int){tri(a,b,c);tri(a,c,d);edge(a,b);edge(b,c);edge(c,d);edge(d,a)}
        if(id=="cube") {
            // Shared eight vertices; flat face normals are split at GLB export.
            for(y in listOf(0f,h))for(z in listOf(-r,r))for(x in listOf(-r,r))vertex(x,y,z,0f,0f,0f)
            for(f in listOf(intArrayOf(0,1,3,2),intArrayOf(4,6,7,5),intArrayOf(0,4,5,1),
                intArrayOf(2,3,7,6),intArrayOf(0,2,6,4),intArrayOf(1,5,7,3)))quad(f[0],f[1],f[2],f[3])
        } else {
            val rings=if(id=="sphere")max(4,(sides/2*(1.0-adaptivity/200.0)).roundToInt()) else 2
            val rows=ArrayList<IntArray>()
            if(id=="sphere") {
                for(j in 1 until rings) {
                    val theta=PI*j/rings;val rr=r*sin(theta).toFloat();val y=h*.5f*(1-cos(theta).toFloat())
                    rows.add(IntArray(sides){k->val a=2*PI*k/sides
                        val nx=sin(theta)*cos(a);val ny=-cos(theta)*2*r/h;val nz=sin(theta)*sin(a)
                        val length=sqrt(nx*nx+ny*ny+nz*nz)
                        vertex(rr*cos(a).toFloat(),y,rr*sin(a).toFloat(),(nx/length).toFloat(),(ny/length).toFloat(),(nz/length).toFloat())})
                }
            } else {
                val count=if(id=="cone")1 else 2
                repeat(count){j->rows.add(IntArray(sides){k->val a=2*PI*k/sides
                    val ny=if(id=="cone")r/h else 0f;val length=sqrt(1f+ny*ny)
                    vertex(r*cos(a).toFloat(),j*h,r*sin(a).toFloat(),cos(a).toFloat()/length,ny/length,sin(a).toFloat()/length)})}
            }
            for(j in 0 until rows.lastIndex)for(k in 0 until sides){val next=(k+1)%sides
                quad(rows[j][k],rows[j+1][k],rows[j+1][next],rows[j][next])}
            val low=vertex(0f,0f,0f,0f,-1f,0f);val high=vertex(0f,h,0f,0f,1f,0f)
            for(k in 0 until sides){val next=(k+1)%sides
                tri(low,rows.first()[k],rows.first()[next])
                tri(high,rows.last()[next],rows.last()[k])
                edge(rows.first()[k],rows.first()[next]);edge(rows.last()[k],rows.last()[next])
                if(id=="sphere" || id=="cone")edge(high,rows.last()[k])
                if(id=="sphere")edge(low,rows.first()[k])
            }
        }
        return Mesh(p.toFloatArray(),n.toFloatArray(),triangles.toIntArray(),edges.toList())
    }
    fun create(id:String,quality:String,grid:Boolean,adaptivity:Int,r:Float,h:Float,width:Float,offset:Float):ByteArray {
        val mesh=mesh(id,quality,adaptivity,r,h)
        val p=ArrayList<Float>();val n=ArrayList<Float>();val indices=ArrayList<Int>()
        fun append(x:Float,y:Float,z:Float,nx:Float,ny:Float,nz:Float){p.addAll(listOf(x,y,z));n.addAll(listOf(nx,ny,nz));indices.add(indices.size)}
        if(grid)for((a,b) in mesh.edges) {
            val ia=a*3;val ib=b*3;val dx=mesh.positions[ib]-mesh.positions[ia]
            val dy=mesh.positions[ib+1]-mesh.positions[ia+1];val dz=mesh.positions[ib+2]-mesh.positions[ia+2]
            var tx=-dz;var ty=0f;var tz=dx
            if(abs(tx)+abs(tz)<1e-8f){tx=1f;ty=0f;tz=0f}
            val length=sqrt(tx*tx+ty*ty+tz*tz);tx*=width/length;ty*=width/length;tz*=width/length
            val base=p.size/3
            for(v in listOf(a,b))for(sign in listOf(-1f,1f)) {
                val i=v*3
                p.addAll(listOf(mesh.positions[i]+tx*sign+mesh.normals[i]*offset,
                    mesh.positions[i+1]+ty*sign+mesh.normals[i+1]*offset,
                    mesh.positions[i+2]+tz*sign+mesh.normals[i+2]*offset))
                n.addAll(listOf(0f,1f,0f))
            }
            indices.addAll(listOf(base,base+2,base+1,base+1,base+2,base+3))
        } else for(i in mesh.indices.indices step 3) {
            val ids=intArrayOf(mesh.indices[i],mesh.indices[i+1],mesh.indices[i+2])
            val a=ids[0]*3;val b=ids[1]*3;val c=ids[2]*3
            val ux=mesh.positions[b]-mesh.positions[a];val uy=mesh.positions[b+1]-mesh.positions[a+1];val uz=mesh.positions[b+2]-mesh.positions[a+2]
            val vx=mesh.positions[c]-mesh.positions[a];val vy=mesh.positions[c+1]-mesh.positions[a+1];val vz=mesh.positions[c+2]-mesh.positions[a+2]
            val nx=uy*vz-uz*vy;val ny=uz*vx-ux*vz;val nz=ux*vy-uy*vx;val length=sqrt(nx*nx+ny*ny+nz*nz)
            for(v in ids){val q=v*3
                val flat=id=="cube" || (id!="sphere" && abs(ny/length)>.999f)
                append(mesh.positions[q],mesh.positions[q+1],mesh.positions[q+2],
                    if(flat)nx/length else mesh.normals[q],if(flat)ny/length else mesh.normals[q+1],if(flat)nz/length else mesh.normals[q+2])
            }
        }
        return ParametricGoblet.toGlb(0,0,grid,p.toFloatArray(),n.toFloatArray(),indices.toIntArray())
    }
}
