package com.glassstudy.filament

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Visualizes a computed photon irradiance map; NEVER paints a synthetic disc.
 * Each vertex stores the signed departure from an unoccluded sunlit floor:
 * photon deficits darken, concentrations brighten.
 */
object GlassCausticsGlb {
    fun create(field:GlassRayTrace.Field,brightness:Double):ByteBuffer? {
        require(brightness.isFinite() && brightness in 0.0..2.0)
        if(field.hits==0 || brightness<.015)return null
        val n=field.size
        val half=field.halfWidth
        val factor=sqrt(brightness/2.0).coerceIn(0.0,1.0)
        val positions=FloatArray((n+1)*(n+1)*3)
        val colors=FloatArray((n+1)*(n+1)*4)
        val indices=IntArray(n*n*6)
        for(z in 0..n)for(x in 0..n) {
            val idx=z*(n+1)+x
            positions[idx*3]=(-half+x*2.0*half/n).toFloat()
            positions[idx*3+1]=-.048f
            positions[idx*3+2]=(-half+z*2.0*half/n).toFloat()
            var flux=0.0;var count=0
            for(dz in -1..0)for(dx in -1..0) {
                val cx=x+dx;val cz=z+dz
                if(cx !in 0 until n || cz !in 0 until n)continue
                flux+=field.flux[cz*n+cx];count++
            }
            val delta=if(count>0)(flux/count-1.0)*factor else 0.0
            val dark=delta<0.0
            val alpha=if(dark) min(.78,-delta*.82)
                else min(.74,ln(1.0+delta.coerceAtLeast(0.0))*.59)
            val color=if(dark).025f else 1f
            colors[idx*4]=color; colors[idx*4+1]=color
            colors[idx*4+2]=color;colors[idx*4+3]=alpha.toFloat()
        }
        var p=0
        for(z in 0 until n)for(x in 0 until n) {
            val a=z*(n+1)+x
            val b=a+n+1
            indices[p++]=a;indices[p++]=b;indices[p++]=a+1
            indices[p++]=a+1;indices[p++]=b;indices[p++]=b+1
        }
        val bin=ByteBuffer.allocate(positions.size*4+colors.size*4+indices.size*4)
            .order(ByteOrder.LITTLE_ENDIAN)
        positions.forEach{bin.putFloat(it)}
        colors.forEach{bin.putFloat(it)}
        indices.forEach{bin.putInt(it)}
        val posBytes=positions.size*4
        val colorBytes=colors.size*4
        val bufferViews=JSONArray()
            .put(JSONObject().put("buffer",0).put("byteOffset",0)
                .put("byteLength",posBytes).put("target",34962))
            .put(JSONObject().put("buffer",0).put("byteOffset",posBytes)
                .put("byteLength",colorBytes).put("target",34962))
            .put(JSONObject().put("buffer",0).put("byteOffset",posBytes+colorBytes)
                .put("byteLength",indices.size*4).put("target",34963))
        val accessors=JSONArray()
            .put(JSONObject().put("bufferView",0).put("componentType",5126)
                .put("count",positions.size/3).put("type","VEC3")
                .put("min",JSONArray(listOf(-half,-.048,-half)))
                .put("max",JSONArray(listOf(half,-.048,half))))
            .put(JSONObject().put("bufferView",1).put("componentType",5126)
                .put("count",colors.size/4).put("type","VEC4"))
            .put(JSONObject().put("bufferView",2).put("componentType",5125)
                .put("count",indices.size).put("type","SCALAR"))
        val mat=JSONObject().put("name","Cached Snell caustics over floor")
            .put("doubleSided",true).put("alphaMode","BLEND")
            .put("extensions",JSONObject().put("KHR_materials_unlit",JSONObject()))
            .put("pbrMetallicRoughness",JSONObject()
                .put("baseColorFactor",JSONArray(listOf(1.0,1.0,1.0,1.0)))
                .put("metallicFactor",0).put("roughnessFactor",1))
        val primitive=JSONObject().put("attributes",
                JSONObject().put("POSITION",0).put("COLOR_0",1))
            .put("indices",2).put("material",0).put("mode",4)
        val gltf=JSONObject().put("asset",JSONObject()
                .put("version","2.0").put("generator","GlassStudy CPU photon map"))
            .put("extensionsUsed",JSONArray().put("KHR_materials_unlit"))
            .put("buffers",JSONArray().put(JSONObject().put("byteLength",bin.capacity())))
            .put("bufferViews",bufferViews).put("accessors",accessors)
            .put("materials",JSONArray().put(mat))
            .put("meshes",JSONArray().put(JSONObject().put("primitives",JSONArray().put(primitive))))
            .put("nodes",JSONArray().put(JSONObject().put("mesh",0)))
            .put("scenes",JSONArray().put(JSONObject().put("nodes",JSONArray().put(0))))
            .put("scene",0)
        val json=gltf.toString().toByteArray(Charsets.UTF_8)
        val aligned=(json.size+3) and -4
        val out=ByteBuffer.allocateDirect(12+8+aligned+8+bin.capacity())
            .order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546c67).putInt(2).putInt(out.capacity())
        out.putInt(aligned).putInt(0x4e4f534a).put(json)
        repeat(aligned-json.size){out.put(0x20.toByte())}
        out.putInt(bin.capacity()).putInt(0x004e4942).put(bin.array())
        out.flip()
        return out
    }
}
