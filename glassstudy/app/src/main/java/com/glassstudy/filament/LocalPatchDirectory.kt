package com.glassstudy.filament

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * Privacy-respecting, scoped local updater.
 *
 * Only a persistently user-approved *single folder* is searched. Never request
 * Android's highly privileged MANAGE_EXTERNAL_STORAGE All files access.
 *
 * Android 11+ cannot grant the root Downloads directory via a tree picker:
 * choose/create a Downloads/GlassStudy_Patches subfolder once, and place the
 * signed .pupdate files there. The persisted SAF grant works after reboot.
 */
class LocalPatchDirectory(private val activity:Activity) {
    private val prefs=activity.getSharedPreferences("glassstudy.local.patches",0)
    private val key="tree_uri"
    val configured:Boolean get()=prefs.getString(key,null)!=null
    var lastScanError:String?=null
        private set

    fun selectedFolder():String =
        prefs.getString("folder_label","папка патчей") ?: "папка патчей"

    fun requestFolder(requestCode:Int) {
        val request=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        activity.startActivityForResult(request,requestCode)
    }

    fun acceptGrant(data:Intent?):Boolean {
        val uri=data?.data ?:return false
        return try {
            activity.contentResolver.takePersistableUriPermission(
                uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString(key,uri.toString())
                .putString("folder_label",uri.lastPathSegment ?: "GlassStudy_Patches").apply()
            true
        } catch(error:Exception) {
            Log.w("GlassStudy","Unable to retain patch folder permission",error)
            false
        }
    }

    /** Ordered candidate URIs; untrusted bytes are later RSA+SHA256 verified. */
    fun candidatesAbove(installed:Int):List<Uri> {
        lastScanError=null
        val stored=prefs.getString(key,null) ?:return emptyList()
        return try {
            val tree=Uri.parse(stored)
            val parent=DocumentsContract.getTreeDocumentId(tree)
            val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent)
            val columns=arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE)
            val matches=mutableListOf<Pair<Int,Uri>>()
            activity.contentResolver.query(children,columns,null,null,null)?.use { c ->
                val id=c.getColumnIndexOrThrow(columns[0])
                val name=c.getColumnIndexOrThrow(columns[1])
                val mime=c.getColumnIndexOrThrow(columns[2])
                while(c.moveToNext()) {
                    if(c.getString(mime)==DocumentsContract.Document.MIME_TYPE_DIR)continue
                    val file=c.getString(name) ?:continue
                    val match=Regex("""(?:GlassStudy_Filament_)?(\d{5})[^/]*\.pupdate""",
                        RegexOption.IGNORE_CASE).matchEntire(file) ?:continue
                    val version=match.groupValues[1].toIntOrNull() ?:continue
                    if(version<=installed)continue
                    matches.add(version to
                        DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(id)))
                }
            }
            matches.sortedByDescending { it.first }.map { it.second }
        } catch(error:Exception) {
            Log.w("GlassStudy","Patch folder unavailable, retain existing folder grant",error)
            lastScanError="${error.javaClass.simpleName}: ${error.message ?: "нет доступа"}"
            // Persisted URI is not forgotten due to transient SAF failure.
            emptyList()
        }
    }

    fun newestAbove(installed:Int):Uri?=candidatesAbove(installed).firstOrNull()
}
