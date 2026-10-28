package com.glassstudy.filament

import android.util.Log
import com.google.android.filament.utils.GlassStudyModelViewer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Model and material lifecycle owner. All model bytes and signed properties
 * are validated before replacing the Filament GLB asset. Failed replacements
 * try to restore the previous active model, without changing patch activation.
 */
class ModelMaterialController(
    private val viewer:GlassStudyModelViewer,
    private val updater:PatchManager
) {
    var configuration=MaterialModuleConfig()
        private set
    var selection=ModelCatalog.defaultSelection
        private set
    var wireframe=false
        private set
    var hasLoaded=false
        private set
    var adaptivePercent=0
        private set
    var opticalContour:FloatArray?=null
        private set
    var analyticOpticalContour:FloatArray?=null
        private set
    var opticalIor=1.52f
        private set

    /** Changes reuse the same profile and density. Commit only after GLB loads. */
    fun setAdaptivity(value:Int):Result<Unit> {
        require(value in 0..100) { "Adaptivity outside 0..100" }
        if(value==adaptivePercent)return Result.success(Unit)
        if(!hasLoaded) {
            adaptivePercent=value
            return Result.success(Unit)
        }
        return load(selection,wireframe,adaptive=value)
    }

    fun configure(next:MaterialModuleConfig) {
        configuration=next
    }

    fun load(
        requested:ModelSelection,
        debug:Boolean,
        candidate:PatchManager.Candidate?=null,
        adaptive:Int=adaptivePercent
    ):Result<Unit> {
        require(adaptive in 0..100)
        val previous=if(hasLoaded)Snapshot(selection,wireframe,adaptivePercent) else null
        var rendererTouched=false
        return try {
            // Candidate model + candidate catalog + candidate overrides come
            // from the SAME verified staging state, never mixed with active config.
            val materialJson=updater.readModuleConfig("materials",candidate)
            val catalog=if(candidate==null)configuration else MaterialModuleConfig.parse(materialJson)
            val overrides=MaterialOverrides.parse(materialJson)
            val bytes=loadMesh(requested,debug,catalog,candidate,adaptive)
            rendererTouched=true
            viewer.loadModelGlb(direct(bytes))
            require(viewer.asset!=null) { "Filament gltfio rejected goblet mesh for "+requested.lod }
            overrides.apply(viewer)
            avoidOpaqueShadowsOnRefractiveGlass(debug)
            opticalContour=if(catalog.resolve(requested.modelId,requested.lod,debug)==null &&
                requested.modelId=="goblet" && (overrides.metallic ?: 0f)==0f &&
                (overrides.transmission ?: 1f)==1f) {
                updater.readResourceOrNull(ParametricGoblet.PATH,candidate)?.let {
                    ParametricGoblet.opticalContour(it,requested.lod,adaptive)
                }
            } else null
            // Fixed reference curve: independent of visible mesh LOD/adaptivity.
            analyticOpticalContour=if(opticalContour!=null)
                updater.readResourceOrNull(ParametricGoblet.PATH,candidate)?.let {
                    ParametricGoblet.opticalContour(it,"ultra",0)
                } else null
            opticalIor=overrides.ior ?: 1.52f
            selection=requested
            wireframe=debug
            adaptivePercent=adaptive
            hasLoaded=true
            Result.success(Unit)
        } catch(error:Exception) {
            Log.w("GlassStudy","Model load rejected; restoring previous scene if needed",error)
            if(rendererTouched && previous!=null) {
                try { restore(previous) }
                catch(restoreError:Exception) {
                    Log.e("GlassStudy","Previous model restoration failed",restoreError)
                    error.addSuppressed(restoreError)
                }
            }
            Result.failure(error)
        }
    }

    /**
     * Gltfio's default opaque shadow map otherwise makes a transmissive
     * goblet cast a solid black silhouette. This is NOT caustics: until a
     * transparent-shadow-capable material is implemented, avoid an invented
     * opaque shadow for refractive primitives. Opaque/grid meshes are untouched.
     */
    private fun avoidOpaqueShadowsOnRefractiveGlass(debug:Boolean) {
        if(debug)return
        val asset=viewer.asset ?: return
        val manager=viewer.engine.renderableManager
        for(entity in asset.entities) {
            val instance=manager.getInstance(entity)
            if(instance==0)continue
            val refractive=(0 until manager.getPrimitiveCount(instance)).any { index ->
                manager.getMaterialInstanceAt(instance,index).material.refractionMode !=
                    com.google.android.filament.Material.RefractionMode.NONE
            }
            if(refractive)manager.setCastShadows(instance,false)
        }
    }

    /** Reads REAL loaded gltfio material state, never just GLB JSON claims. */
    fun glassMaterialDiagnostics():String {
        val asset=viewer.asset ?: return "GLB: модель не загружена"
        val manager=viewer.engine.renderableManager
        val records=mutableListOf<String>()
        for(entity in asset.entities) {
            val instance=manager.getInstance(entity)
            if(instance==0)continue
            for(index in 0 until manager.getPrimitiveCount(instance)) {
                val material=manager.getMaterialInstanceAt(instance,index).material
                records.add(
                    "${material.name}: refraction=${material.refractionMode}, " +
                    "type=${material.refractionType}, transmissionParam=${material.hasParameter("transmissionFactor")}, " +
                    "iorParam=${material.hasParameter("ior")}, " +
                    "opaqueShadow=${manager.isShadowCaster(instance)}")
            }
        }
        return if(records.isEmpty())"GLB: нет отображаемых материалов"
            else records.distinct().joinToString(" | ")
    }

    private data class Snapshot(val selection:ModelSelection,val wireframe:Boolean,val adaptive:Int)

    private fun restore(snapshot:Snapshot) {
        val bytes=loadMesh(snapshot.selection,snapshot.wireframe,configuration,null,snapshot.adaptive)
        val overrides=MaterialOverrides.parse(updater.readModuleConfig("materials"))
        viewer.loadModelGlb(direct(bytes))
        require(viewer.asset!=null) { "Cannot restore goblet mesh for "+snapshot.selection.lod }
        overrides.apply(viewer)
        avoidOpaqueShadowsOnRefractiveGlass(snapshot.wireframe)
    }

    /**
     * One shape, varying only tessellation density. Previously signed GLB
     * overrides remain usable when explicitly mapped in the material catalog.
     */
    private fun loadMesh(selected:ModelSelection,debug:Boolean,
                         catalog:MaterialModuleConfig,candidate:PatchManager.Candidate?,
                         adaptive:Int):ByteArray {
        val override=catalog.resolve(selected.modelId,selected.lod,debug)
        if(override!=null) return readCheckedResource(override,candidate)
        require(selected.modelId=="goblet")
        val profile=updater.readResourceOrNull(ParametricGoblet.PATH,candidate)
            ?: throw IllegalArgumentException("Missing canonical goblet profile")
        return ParametricGoblet.create(profile,selected.lod,debug,adaptive)
    }

    private fun readCheckedResource(path:String,candidate:PatchManager.Candidate?):ByteArray {
        val bytes=updater.readResourceOrNull(path,candidate)
            ?: throw IllegalArgumentException("Missing material/model asset: "+path)
        PatchResourceRegistry.validate(path,bytes)
        return bytes
    }

    private fun direct(bytes:ByteArray):ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes);flip()
        }
}
