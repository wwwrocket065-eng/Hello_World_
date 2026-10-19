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

    fun configure(next:MaterialModuleConfig) {
        configuration=next
    }

    fun load(
        requested:ModelSelection,
        debug:Boolean,
        candidate:PatchManager.Candidate?=null
    ):Result<Unit> {
        val previous=if(hasLoaded)Snapshot(selection,wireframe) else null
        var rendererTouched=false
        return try {
            // Candidate model + candidate catalog + candidate overrides come
            // from the SAME verified staging state, never mixed with active config.
            val materialJson=updater.readModuleConfig("materials",candidate)
            val catalog=if(candidate==null)configuration else MaterialModuleConfig.parse(materialJson)
            val overrides=MaterialOverrides.parse(materialJson)
            val path=resolveResource(requested,debug,catalog)
            val bytes=readCheckedResource(path,candidate)
            rendererTouched=true
            viewer.loadModelGlb(direct(bytes))
            require(viewer.asset!=null) { "Filament gltfio rejected GLB: "+path }
            overrides.apply(viewer)
            selection=requested
            wireframe=debug
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

    private data class Snapshot(val selection:ModelSelection,val wireframe:Boolean)

    private fun restore(snapshot:Snapshot) {
        val path=resolveResource(snapshot.selection,snapshot.wireframe,configuration)
        val bytes=readCheckedResource(path,null)
        val overrides=MaterialOverrides.parse(updater.readModuleConfig("materials"))
        viewer.loadModelGlb(direct(bytes))
        require(viewer.asset!=null) { "Cannot restore model: "+path }
        overrides.apply(viewer)
    }

    private fun resolveResource(selected:ModelSelection,debug:Boolean,catalog:MaterialModuleConfig):String {
        val name=selected.modelId+"_"+selected.lod+(if(debug)"_debug" else "")
        val path=catalog.resolve(selected.modelId,selected.lod,debug) ?: ("models/"+name+".glb")
        require(PatchResourceRegistry.accepts(path) && path.endsWith(".glb")) {
            "Unsafe material/model path: "+path
        }
        return path
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
