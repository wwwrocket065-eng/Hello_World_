package com.glassstudy.filament

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Mobile-friendly, deterministic projected glass-light approximation.
 *
 * This is deliberately NOT path-traced caustics: a translucent unlit receiver
 * over the existing floor suggests attenuated transmission and two focused
 * bright rims (foot and bowl). It follows the SAME physical sun direction as
 * Filament's direct light, and is absent when the light cannot reach the floor.
 * Separate from the floor and goblet assets; never alters either material.
 */
object GlassCausticsGlb {
    private const val SECTORS = 96
    private const val RINGS = 32
    private const val FLOOR_Y = -0.06f
    private fun gauss(x:Double):Double = exp(-x*x)

    fun create(azimuth:Double,elevation:Double,brightness:Double):ByteBuffer? {
        require(azimuth.isFinite() && elevation.isFinite() && elevation in 0.0..PI)
        require(brightness.isFinite() && brightness in 0.0..2.0)
        val light=LightDirectionMath.source(azimuth,elevation)
        if (brightness < .015 || light.y < .12f) return null

        val horizontal=hypot(light.x.toDouble(),light.z.toDouble())
        val axisX=if(horizontal<1e-6)sin(azimuth) else -light.x/horizontal
        val axisZ=if(horizontal<1e-6)cos(azimuth) else -light.z/horizontal
        val perpendicularX=-axisZ
        val perpendicularZ=axisX
        // The bowl projects along incoming rays. Keep grazing incidence bounded
        // to the existing finite floor rather than allocating enormous meshes.
        val projected=(2.05*horizontal/light.y).coerceIn(0.0,2.55)
        val exposure=sqrt(brightness/2.0).coerceIn(0.0,1.0)
        val positions=ArrayList<Float>(2*(RINGS+1)*(SECTORS+1)*3)
        val colors=ArrayList<Float>(2*(RINGS+1)*(SECTORS+1)*4)
        val indices=ArrayList<Int>(2*RINGS*SECTORS*6)

        fun disc(centerX:Double,centerZ:Double,
                 axisLength:Double,acrossLength:Double,foot:Boolean) {
            val start=positions.size/3
            for (i in 0..RINGS) {
                val radius=i.toDouble()/RINGS
                for (j in 0..SECTORS) {
                    val angle=2.0*PI*j/SECTORS
                    val along=radius*cos(angle)*axisLength
                    val across=radius*sin(angle)*acrossLength
                    val px=centerX+axisX*along+perpendicularX*across
                    val pz=centerZ+axisZ*along+perpendicularZ*across
                    positions.add(px.toFloat())
                    positions.add(if(foot) FLOOR_Y+.012f else FLOOR_Y+.009f)
                    positions.add(pz.toFloat())

                    // RGB and alpha are computed together: darkened soft
                    // transmitted-light region, with compact white focused rim.
                    // No black hard-edged fake glass silhouette.
                    val r=radius*1.45
                    val envelope=gauss(r/.85)
                    val shadow=(if(foot) .12 else .23)*gauss(r/1.02)*exposure
                    val focus=gauss((r-(if(foot) .75 else .79))/
                        (if(foot) .060 else .082))
                    val secondary=gauss((r-(if(foot) .42 else 1.02))/.095)
                    val anisotropy=.87+.13*cos(2*angle+azimuth)
                    val lightGain=(if(foot) .57 else .61)*focus*anisotropy +
                        (if(foot) .12 else .15)*secondary
                    val bright=lightGain*exposure
                    val falloff=(1.0-gauss((1.45-r)/.14)).coerceIn(0.0,1.0)
                    val dark=shadow*falloff
                    val alpha=(dark+bright*falloff).coerceIn(0.0,.78)
                    val gray=if(alpha>1e-6)
                        ((dark*.025+bright*falloff*1.0)/alpha).coerceIn(0.0,1.0)
                        else 1.0
                    colors.add(gray.toFloat()); colors.add(gray.toFloat())
                    colors.add(gray.toFloat()); colors.add(alpha.toFloat())
                }
            }
            for (i in 0 until RINGS) for (j in 0 until SECTORS) {
                val a=start+i*(SECTORS+1)+j
                val b=a+SECTORS+1
                indices.add(a);indices.add(b);indices.add(a+1)
                indices.add(a+1);indices.add(b);indices.add(b+1)
            }
        }

        // A short focus at the thick glass foot, and a stretched projected
        // focus from the curved wine bowl, aligned to the incoming sun rays.
        disc(0.0,0.0,.80,.80,true)
        disc(axisX*projected,axisZ*projected,
            1.35+.50*horizontal,1.20,false)

        val posSize=positions.size*4
        val colorSize=colors.size*4
        val idxSize=indices.size*4
        val bin=ByteBuffer.allocate(posSize+colorSize+idxSize)
            .order(ByteOrder.LITTLE_ENDIAN)
        positions.forEach { bin.putFloat(it) }
        colors.forEach { bin.putFloat(it) }
        indices.forEach { bin.putInt(it) }
        val views=JSONArray()
            .put(JSONObject().put("buffer",0).put("byteOffset",0)
                .put("byteLength",posSize).put("target",34962))
            .put(JSONObject().put("buffer",0).put("byteOffset",posSize)
                .put("byteLength",colorSize).put("target",34962))
            .put(JSONObject().put("buffer",0).put("byteOffset",posSize+colorSize)
                .put("byteLength",idxSize).put("target",34963))
        val bounds=positions.indices.step(3).toList()
        val px=bounds.map { positions[it] }
        val py=bounds.map { positions[it+1] }
        val pz=bounds.map { positions[it+2] }
        val accessors=JSONArray()
            .put(JSONObject().put("bufferView",0).put("componentType",5126)
                .put("count",positions.size/3).put("type","VEC3")
                .put("min",JSONArray(listOf(px.minOrNull(),py.minOrNull(),pz.minOrNull())))
                .put("max",JSONArray(listOf(px.maxOrNull(),py.maxOrNull(),pz.maxOrNull()))))
            .put(JSONObject().put("bufferView",1).put("componentType",5126)
                .put("count",colors.size/4).put("type","VEC4"))
            .put(JSONObject().put("bufferView",2).put("componentType",5125)
                .put("count",indices.size).put("type","SCALAR"))
        val material=JSONObject().put("name","Approximate sunlight through glass")
            .put("doubleSided",true).put("alphaMode","BLEND")
            .put("extensions",JSONObject().put("KHR_materials_unlit",JSONObject()))
            .put("pbrMetallicRoughness",JSONObject()
                .put("baseColorFactor",JSONArray(listOf(1.0,1.0,1.0,1.0)))
                .put("metallicFactor",0).put("roughnessFactor",1))
        val primitive=JSONObject().put("attributes",JSONObject()
            .put("POSITION",0).put("COLOR_0",1))
            .put("indices",2).put("material",0).put("mode",4)
        val gltf=JSONObject().put("asset",JSONObject().put("version","2.0")
                .put("generator","GlassStudy mobile projected glass light"))
            .put("extensionsUsed",JSONArray().put("KHR_materials_unlit"))
            .put("buffers",JSONArray().put(JSONObject().put("byteLength",bin.capacity())))
            .put("bufferViews",views).put("accessors",accessors)
            .put("materials",JSONArray().put(material))
            .put("meshes",JSONArray().put(JSONObject().put("primitives",JSONArray().put(primitive))))
            .put("nodes",JSONArray().put(JSONObject().put("mesh",0)))
            .put("scenes",JSONArray().put(JSONObject().put("nodes",JSONArray().put(0))))
            .put("scene",0)
        val json=gltf.toString().toByteArray(Charsets.UTF_8)
        val padded=(json.size+3) and -4
        val result=ByteBuffer.allocateDirect(12+8+padded+8+bin.capacity())
            .order(ByteOrder.LITTLE_ENDIAN)
        result.putInt(0x46546c67).putInt(2).putInt(result.capacity())
        result.putInt(padded).putInt(0x4e4f534a).put(json)
        repeat(padded-json.size){result.put(0x20.toByte())}
        result.putInt(bin.capacity()).putInt(0x004e4942).put(bin.array())
        result.flip()
        return result
    }
}
