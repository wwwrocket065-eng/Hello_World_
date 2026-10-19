package com.glassstudy.filament

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Self-contained glTF 2.0 plane. Separate floor asset, independent of selected model. */
object SceneFloorGlb {
    fun create(checker: Boolean): ByteBuffer {
        val cells = if (checker) 10 else 1
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val indices = arrayOf(ArrayList<Int>(), ArrayList<Int>())
        val width = 6f
        for (y in 0 until cells) for (x in 0 until cells) {
            val x0 = -width / 2 + width * x / cells
            val x1 = -width / 2 + width * (x + 1) / cells
            val z0 = -width / 2 + width * y / cells
            val z1 = -width / 2 + width * (y + 1) / cells
            val start = positions.size / 3
            for ((px,pz) in listOf(x0 to z0, x1 to z0, x1 to z1, x0 to z1)) {
                positions.add(px); positions.add(-0.06f); positions.add(pz)
                normals.add(0f); normals.add(1f); normals.add(0f)
            }
            val dst = indices[if(checker && (x + y) % 2 != 0) 1 else 0]
            dst.addAll(listOf(start,start+2,start+1,start,start+3,start+2))
        }
        fun align4(n: Int) = (n + 3) and -4
        val posSize = positions.size * 4
        val normalSize = normals.size * 4
        val idx0Size = indices[0].size * 2
        val idx1Size = indices[1].size * 2
        val offs = intArrayOf(0,align4(posSize),align4(posSize)+align4(normalSize),
            align4(posSize)+align4(normalSize)+align4(idx0Size))
        val bin = ByteBuffer.allocate(align4(offs[3] + idx1Size)).order(ByteOrder.LITTLE_ENDIAN)
        bin.position(offs[0]); positions.forEach { bin.putFloat(it) }
        bin.position(offs[1]); normals.forEach { bin.putFloat(it) }
        bin.position(offs[2]); indices[0].forEach { bin.putShort(it.toShort()) }
        bin.position(offs[3]); indices[1].forEach { bin.putShort(it.toShort()) }
        val views = JSONArray()
        fun view(offset: Int, size: Int, target: Int): Int {
            val id = views.length()
            views.put(JSONObject().put("buffer",0).put("byteOffset",offset)
                .put("byteLength",size).put("target",target))
            return id
        }
        val pv=view(offs[0],posSize,34962)
        val nv=view(offs[1],normalSize,34962)
        val iv0=view(offs[2],idx0Size,34963)
        val iv1=if(idx1Size>0)view(offs[3],idx1Size,34963) else -1
        val accesses=JSONArray()
        fun access(view:Int,component:Int,count:Int,type:String,min:JSONArray?=null,max:JSONArray?=null):Int {
            val id=accesses.length()
            val item=JSONObject().put("bufferView",view).put("componentType",component)
                .put("count",count).put("type",type)
            if(min!=null)item.put("min",min)
            if(max!=null)item.put("max",max)
            accesses.put(item)
            return id
        }
        val p=access(pv,5126,positions.size/3,"VEC3",JSONArray(listOf(-3.0,-0.06,-3.0)),
            JSONArray(listOf(3.0,-0.06,3.0)))
        val n=access(nv,5126,normals.size/3,"VEC3")
        val index0=access(iv0,5123,indices[0].size,"SCALAR")
        val index1=if(iv1>=0) access(iv1,5123,indices[1].size,"SCALAR") else -1
        val materials=JSONArray()
        fun material(rgb: Double):JSONObject =
            JSONObject().put("doubleSided",true).put("pbrMetallicRoughness",JSONObject()
                .put("baseColorFactor",JSONArray(listOf(rgb,rgb,rgb,1.0)))
                .put("metallicFactor",0).put("roughnessFactor",0.94))
        materials.put(material(if(checker) 0.83 else 0.96))
        materials.put(material(0.23))
        val primitives=JSONArray()
        fun primitive(index:Int,mat:Int) = JSONObject().put("attributes",
            JSONObject().put("POSITION",p).put("NORMAL",n))
            .put("indices",index).put("material",mat).put("mode",4)
        primitives.put(primitive(index0,0))
        if(index1>=0)primitives.put(primitive(index1,1))
        val gltf=JSONObject().put("asset",JSONObject().put("version","2.0"))
            .put("buffers",JSONArray().put(JSONObject().put("byteLength",bin.capacity())))
            .put("bufferViews",views).put("accessors",accesses)
            .put("materials",materials).put("meshes",JSONArray().put(JSONObject().put("primitives",primitives)))
            .put("nodes",JSONArray().put(JSONObject().put("mesh",0)))
            .put("scenes",JSONArray().put(JSONObject().put("nodes",JSONArray().put(0))))
            .put("scene",0)
        val json=gltf.toString().toByteArray(Charsets.UTF_8)
        val jSize=align4(json.size)
        val total=12+8+jSize+8+bin.capacity()
        val result=ByteBuffer.allocateDirect(total).order(ByteOrder.LITTLE_ENDIAN)
        result.putInt(0x46546C67);result.putInt(2);result.putInt(total)
        result.putInt(jSize);result.putInt(0x4E4F534A);result.put(json)
        repeat(jSize-json.size){result.put(0x20.toByte())}
        result.putInt(bin.capacity());result.putInt(0x004E4942)
        result.put(bin.array())
        result.flip()
        return result
    }
}
