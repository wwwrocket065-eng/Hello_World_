package com.glassstudy.filament

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * Scoped-storage-safe local update source. The user grants ONE folder with
 * ACTION_OPEN_DOCUMENT_TREE; a persisted read grant is reused on every launch.
 * Never requests MANAGE_EXTERNAL_STORAGE or guesses a physical Downloads path.
 */
class LocalPatchDirectory(private val activity:Activity) {
    private val prefs=activity.getSharedPreferences("glassstudy.local.patches",0)
    private val key="tree_uri"
    val configured:Boolean get()=prefs.getString(key,null)!=null
    fun selectedFolder():String=prefs.getString("folder_label","папка патчей") ?: "папка патчей"

    fun requestFolder(requestCode:Int) {
        // Android 11+ blocks granting the Downloads ROOT. Use a user-created
        // Downloads/GlassStudy_Patches subfolder with persistent read access.
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

    /** Return only the highest new .pupdate. Content is untrusted until RSA stage(). */
    fun newestAbove(installed:Int):Uri? {
        val stored=prefs.getString(key,null) ?:return null
        return try {
            val tree=Uri.parse(stored)
            val parent=DocumentsContract.getTreeDocumentId(tree)
            val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent)
            val columns=arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE)
            var chosen:Uri?=null
            var maxVersion=installed
            activity.contentResolver.query(children,columns,null,null,null)?.use { c ->
                val id=c.getColumnIndexOrThrow(columns[0])
                val name=c.getColumnIndexOrThrow(columns[1])
                val mime=c.getColumnIndexOrThrow(columns[2])
                while(c.moveToNext()) {
                    if(c.getString(mime)==DocumentsContract.Document.MIME_TYPE_DIR)continue
                    val file=c.getString(name) ?:continue
                    val match=Regex("""(?:GlassStudy_Filament_)?(\d{5})[^/]*\.pupdate""",
                        RegexOption.IGNORE_CASE).matchEntire(file) ?: continue
                    val v=match.groupValues[1].toIntOrNull() ?:continue
                    if(v<=maxVersion)continue
                    maxVersion=v
                    chosen=DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(id))
                }
            }
            chosen
        } catch(error:Exception) {
            Log.w("GlassStudy","Patch folder unavailable; grant can be changed",error)
            prefs.edit().remove(key).remove("folder_label").apply()
            null
        }
    }
}
