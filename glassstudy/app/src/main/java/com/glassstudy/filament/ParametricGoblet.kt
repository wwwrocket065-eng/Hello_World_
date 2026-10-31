package com.glassstudy.filament

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * One editable wine-glass cross section, tessellated ON DEMAND. Both the real
 * glass and black topological grid share the same vertices/shape. The
 * density setting adjusts polygon counts; it never selects another glass.
 */
object ParametricGoblet {
    const val PATH = "catalogs/goblet.json"
    private val density = mapOf(
        "low" to (14 to 38), "medium" to (28 to 74),
        "high" to (56 to 142), "ultra" to (112 to 280)
    )
    private data class Ring(val y: Double, val r: Double)
    private data class Shape(val points: List<Ring>, val width: Double, val offset: Double)

    fun validate(bytes: ByteArray) { parse(bytes) }
    /** Shared signed profile sampling for the optical pass, not a second shape. */
    fun opticalContour(bytes:ByteArray,quality:String,adaptivePercent:Int):FloatArray {
        val shape=parse(bytes)
        val rings=density[quality]?.second ?: error("Unsupported mesh quality")
        val contour=simplify(sample(shape,rings,adaptivePercent),quality,adaptivePercent)
        val result=ArrayList<Float>()
        result.add(contour.first().y.toFloat());result.add(0f)
        contour.forEach { result.add(it.y.toFloat());result.add(it.r.toFloat()) }
        result.add(contour.last().y.toFloat());result.add(0f)
        return result.toFloatArray()
    }
    private fun parse(bytes: ByteArray): Shape {
        require(bytes.size in 80..32768) { "Invalid goblet profile size" }
        val obj = JSONObject(String(bytes, Charsets.UTF_8))
        require(obj.getInt("schema") == 1) { "Unsupported goblet profile schema" }
        val array = obj.getJSONArray("profile")
        require(array.length() in 24..80)
        val points = (0 until array.length()).map {
            val pair = array.getJSONArray(it)
            require(pair.length() == 2)
            val y = pair.getDouble(0); val r = pair.getDouble(1)
            require(y.isFinite() && y in 0.0..10.0 && r.isFinite() && r in 0.001..3.0)
            Ring(y, r)
        }
        for (i in 1 until points.size) {
            require(hypot(points[i].y-points[i-1].y,points[i].r-points[i-1].r) > .002)
        }
        val width = obj.getDouble("wireThickness")
        val offset = obj.getDouble("wireOffset")
        require(width.isFinite() && width in 0.0003..0.01)
        require(offset.isFinite() && offset in 0.0005..0.02)
        return Shape(points,width,offset)
    }

    private fun sample(shape: Shape, count: Int, adaptivePercent: Int): List<Ring> {
        val source = shape.points
        fun cubic(a: Double,b: Double,c: Double,d: Double,t:Double) =
            .5 * (2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t)
        val dense = ArrayList<Ring>()
        for (j in 0 until source.lastIndex) for (k in 0 until 16) {
            val a=source[max(0,j-1)]; val b=source[j]
            val c=source[j+1]; val d=source[min(source.lastIndex,j+2)]
            val t=k/16.0
            dense.add(Ring(cubic(a.y,b.y,c.y,d.y,t),
                max(.001,cubic(a.r,b.r,c.r,d.r,t))))
        }
        dense.add(source.last())
        // Keep one fixed segment budget. At 0%, space rings uniformly by arc
        // length; increasing adaptation redistributes rings to high curvature.
        // This is geometric tessellation, unrelated to FXAA anti-aliasing.
        require(adaptivePercent in 0..100)
        val strength=adaptivePercent/100.0
        val length=DoubleArray(dense.size)
        for (i in 1 until dense.size) {
            val ds=hypot(dense[i].y-dense[i-1].y,dense[i].r-dense[i-1].r)
            val a=dense[max(0,i-2)]
            val b=dense[i-1]
            val c=dense[min(dense.lastIndex,i+1)]
            val ux=b.y-a.y; val ur=b.r-a.r
            val vx=c.y-b.y; val vr=c.r-b.r
            val norm=hypot(ux,ur)*hypot(vx,vr)
            val cosine=if(norm>1e-12) ((ux*vx+ur*vr)/norm).coerceIn(-1.0,1.0) else 1.0
            val curvature=acos(cosine)/max(1e-5,hypot(c.y-a.y,c.r-a.r))
            val priority=1.0+strength*min(5.0,curvature*.45)
            length[i]=length[i-1]+ds*priority
        }
        var cursor=0
        return (0 until count).map { j ->
            val target=length.last()*j/(count-1)
            while(cursor<length.size-2 && length[cursor+1]<target)cursor++
            val t=((target-length[cursor])/max(1e-9,length[cursor+1]-length[cursor])).coerceIn(0.0,1.0)
            Ring(dense[cursor].y*(1-t)+dense[cursor+1].y*t,
                 dense[cursor].r*(1-t)+dense[cursor+1].r*t)
        }
    }

    /**
     * Remove actual cross-section rings where the profile is already close to
     * a straight segment. Unlike curvature weighting this reduces quads.
     * Quality chooses the maximum geometric error; the slider chooses strength.
     * Endpoints and bends are preserved within that tolerance.
     */
    private fun simplify(profile:List<Ring>,quality:String,adaptivePercent:Int):List<Ring> {
        if(adaptivePercent==0 || profile.size<=3)return profile
        val maxError=when(quality) {
            "low" -> .014
            "medium" -> .007
            "high" -> .0035
            "ultra" -> .0015
            else -> error("Unsupported mesh quality")
        }
        val threshold=maxError*(adaptivePercent/100.0).pow(2.0)
        val kept=BooleanArray(profile.size)
        kept[0]=true;kept[profile.lastIndex]=true
        val work=java.util.ArrayDeque<Pair<Int,Int>>()
        work.add(0 to profile.lastIndex)
        while(!work.isEmpty()) {
            val (start,end)=work.removeLast()
            val from=profile[start];val to=profile[end]
            val dy=to.y-from.y;val dr=to.r-from.r
            val lengthSquared=dy*dy+dr*dr
            var worst=-1.0;var split=-1
            for(i in start+1 until end) {
                val point=profile[i]
                val t=if(lengthSquared>1e-18)
                    (((point.y-from.y)*dy+(point.r-from.r)*dr)/lengthSquared).coerceIn(0.0,1.0)
                    else 0.0
                val error=hypot(point.y-(from.y+t*dy),point.r-(from.r+t*dr))
                if(error>worst){worst=error;split=i}
            }
            if(split>=0 && worst>threshold) {
                kept[split]=true
                work.add(start to split)
                work.add(split to end)
            }
        }
        val result=profile.filterIndexed { index,_ -> kept[index] }
        return if(result.size>=8)result else profile
    }

    fun create(bytes:ByteArray, quality:String, grid:Boolean, adaptivePercent:Int=0):ByteArray {
        val shape=parse(bytes)
        val (sides,rings)=density[quality] ?: error("Unsupported mesh density: $quality")
        val contour=simplify(sample(shape,rings,adaptivePercent),quality,adaptivePercent)
        val actualRings=contour.size
        // Two axis vertices close only the existing tiny end-ring holes.
        val capStart=sides*actualRings
        val capEnd=capStart+1
        val sourcePositions=FloatArray((capStart+2)*3)
        val sourceNormals=FloatArray(sourcePositions.size)
        for(j in 0 until actualRings) {
            val prev=contour[max(j-1,0)];val next=contour[min(j+1,actualRings-1)]
            val dy=next.y-prev.y;val dr=next.r-prev.r
            val len=max(1e-10,hypot(dy,dr))
            for(k in 0 until sides) {
                val angle=2*PI*k/sides;val c=cos(angle);val s=sin(angle)
                val i=(j*sides+k)*3
                sourcePositions[i]=(contour[j].r*c).toFloat()
                sourcePositions[i+1]=contour[j].y.toFloat()
                sourcePositions[i+2]=(contour[j].r*s).toFloat()
                sourceNormals[i]=(dy*c/len).toFloat()
                sourceNormals[i+1]=(-dr/len).toFloat()
                sourceNormals[i+2]=(dy*s/len).toFloat()
            }
        }
        sourcePositions[capStart*3+1]=contour.first().y.toFloat()
        sourcePositions[capEnd*3+1]=contour.last().y.toFloat()
        sourceNormals[capStart*3+1]=-1f
        sourceNormals[capEnd*3+1]=1f
        val positions:FloatArray
        val normals:FloatArray
        val indices:IntArray
        if(!grid) {
            positions=sourcePositions;normals=sourceNormals
            indices=IntArray((actualRings-1)*sides*6+sides*6)
            var p=0
            for(j in 0 until actualRings-1)for(k in 0 until sides){
                val a=j*sides+k;val b=j*sides+(k+1)%sides
                val c=a+sides;val d=b+sides
                indices[p++]=a;indices[p++]=c;indices[p++]=b
                indices[p++]=b;indices[p++]=c;indices[p++]=d
            }
            for(k in 0 until sides) {
                val next=(k+1)%sides
                indices[p++]=capStart;indices[p++]=k;indices[p++]=next
                val last=(actualRings-1)*sides
                indices[p++]=capEnd;indices[p++]=last+next;indices[p++]=last+k
            }
            check(p==indices.size)
        } else {
            val edges=sides*(2*actualRings-1)
            positions=FloatArray(edges*12)
            normals=FloatArray(edges*12)
            indices=IntArray(edges*6)
            var edge=0
            fun append(a:Int,b:Int){
                val ia=a*3;val ib=b*3
                val dx=sourcePositions[ib]-sourcePositions[ia]
                val dy=sourcePositions[ib+1]-sourcePositions[ia+1]
                val dz=sourcePositions[ib+2]-sourcePositions[ia+2]
                val nx=sourceNormals[ia]+sourceNormals[ib]
                val ny=sourceNormals[ia+1]+sourceNormals[ib+1]
                val nz=sourceNormals[ia+2]+sourceNormals[ib+2]
                var tx=dy*nz-dz*ny;var ty=dz*nx-dx*nz;var tz=dx*ny-dy*nx
                var tl=sqrt(tx*tx+ty*ty+tz*tz)
                if(tl<1e-9f) {
                    tx=-dz;ty=0f;tz=dx
                    tl=max(1e-9f,sqrt(tx*tx+tz*tz))
                }
                for(end in 0..1)for(sign in 0..1) {
                    val src=if(end==0)ia else ib
                    val dst=edge*12+end*6+sign*3
                    val side=if(sign==0)-1f else 1f
                    val t=floatArrayOf(tx,ty,tz)
                    for(axis in 0..2) {
                        positions[dst+axis]=sourcePositions[src+axis]+
                            sourceNormals[src+axis]*shape.offset.toFloat()+
                            side*shape.width.toFloat()*t[axis]/tl
                        normals[dst+axis]=sourceNormals[src+axis]
                    }
                }
                val v=edge*4;val i=edge*6
                indices[i]=v;indices[i+1]=v+2;indices[i+2]=v+1
                indices[i+3]=v+1;indices[i+4]=v+2;indices[i+5]=v+3
                edge++
            }
            for(j in 0 until actualRings)for(k in 0 until sides){
                val a=j*sides+k
                append(a,j*sides+(k+1)%sides)
                if(j<actualRings-1)append(a,a+sides)
            }
            check(edge==edges)
        }
        return toGlb(sides,actualRings,grid,positions,normals,indices)
    }

    internal fun toGlb(sides:Int,rings:Int,grid:Boolean,
                      positions:FloatArray,normals:FloatArray,indices:IntArray):ByteArray {
        val pSize=positions.size*4;val nSize=normals.size*4;val iSize=indices.size*4
        val bin=ByteBuffer.allocate(pSize+nSize+iSize).order(ByteOrder.LITTLE_ENDIAN)
        positions.forEach{bin.putFloat(it)}
        normals.forEach{bin.putFloat(it)}
        indices.forEach{bin.putInt(it)}
        fun array(vararg items:Any):JSONArray=JSONArray().apply{items.forEach{put(it)}}
        val minv=DoubleArray(3){Double.POSITIVE_INFINITY}
        val maxv=DoubleArray(3){Double.NEGATIVE_INFINITY}
        for(i in positions.indices step 3)for(axis in 0..2){
            val x=positions[i+axis].toDouble()
            minv[axis]=min(minv[axis],x);maxv[axis]=max(maxv[axis],x)
        }
        fun view(offset:Int,length:Int,target:Int)=JSONObject().put("buffer",0)
            .put("byteOffset",offset).put("byteLength",length).put("target",target)
        fun access(view:Int,type:Int,count:Int,kind:String)=JSONObject()
            .put("bufferView",view).put("componentType",type).put("count",count).put("type",kind)
        val p=access(0,5126,positions.size/3,"VEC3")
            .put("min",array(*minv.toTypedArray())).put("max",array(*maxv.toTypedArray()))
        val n=access(1,5126,normals.size/3,"VEC3")
        val i=access(2,5125,indices.size,"SCALAR")
        val extensions=if(grid) JSONObject().put("KHR_materials_unlit",JSONObject()) else
            JSONObject().put("KHR_materials_transmission",JSONObject().put("transmissionFactor",1.0))
                .put("KHR_materials_ior",JSONObject().put("ior",1.52))
        val material=JSONObject().put("name",if(grid)"Black mesh without fill" else "Physical thin-shell glass")
            .put("doubleSided",true)
            .put("pbrMetallicRoughness",JSONObject()
                .put("baseColorFactor",if(grid)array(0,0,0,1) else array(.99,1,1,1))
                .put("metallicFactor",0).put("roughnessFactor",if(grid)1.0 else .028))
            .put("extensions",extensions)
        val gltf=JSONObject()
            .put("asset",JSONObject().put("version","2.0").put("generator","GlassStudy adaptive single goblet"))
            .put("extensionsUsed",if(grid)array("KHR_materials_unlit") else
                array("KHR_materials_transmission","KHR_materials_ior"))
            .put("scene",0).put("scenes",array(JSONObject().put("nodes",array(0))))
            .put("nodes",array(JSONObject().put("mesh",0)))
            .put("meshes",array(JSONObject().put("name","Goblet").put("primitives",
                array(JSONObject().put("attributes",JSONObject().put("POSITION",0).put("NORMAL",1))
                    .put("indices",2).put("material",0).put("mode",4)))))
            .put("materials",array(material))
            .put("bufferViews",array(view(0,pSize,34962),view(pSize,nSize,34962),
                view(pSize+nSize,iSize,34963)))
            .put("accessors",array(p,n,i))
            .put("buffers",array(JSONObject().put("byteLength",bin.capacity())))
            .put("extras",JSONObject().put("quadGrid",JSONObject()
                .put("radialSegments",sides).put("profileSegments",rings-1)
                .put("quads",sides*(rings-1)).put("visibleEdges",sides*(2*rings-1))
                .put("filled",!grid)))
        val json=gltf.toString().toByteArray(Charsets.UTF_8)
        val aligned=(json.size+3) and -4
        val out=ByteBuffer.allocate(12+8+aligned+8+bin.capacity()).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546c67).putInt(2).putInt(out.capacity())
        out.putInt(aligned).putInt(0x4e4f534a).put(json)
        repeat(aligned-json.size){out.put(0x20.toByte())}
        out.putInt(bin.capacity()).putInt(0x004e4942).put(bin.array())
        return out.array()
    }
}
