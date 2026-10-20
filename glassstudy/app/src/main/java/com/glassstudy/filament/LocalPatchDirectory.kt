package com.glassstudy.filament

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log

/**
 * Scoped-storage-safe local update source. The user grants ONE folder with
 * ACTION_OPEN_DOCUMENT_TREE; a persisted read grant is reused on every launch.
 * Optional user-approved Downloads access, with persisted SAF fallback.
 */
class LocalPatchDirectory(private val activity:Activity) {
    private val prefs=activity.getSharedPreferences("glassstudy.local.patches",0)
    private val key="tree_uri"
    val configured:Boolean get()=hasDownloadsAccess() || prefs.getString(key,null)!=null
    fun hasDownloadsAccess():Boolean=Build.VERSION.SDK_INT>=30 && Environment.isExternalStorageManager()
    fun requestDownloadsAccess() {
        if(Build.VERSION.SDK_INT<30) { requestFolder(8124); return }
        val request=Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:"+activity.packageName))
        try { activity.startActivity(request) }
        catch (_:android.content.ActivityNotFoundException) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
    var lastScanError:String?=null
        private set
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

    /** Ranked candidates. Files are untrusted until PatchManager verifies their signatures. */
    fun candidatesAbove(installed:Int):List<Uri> {
        lastScanError=null
        if(hasDownloadsAccess()) {
            return try {
                val directory=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val regex=Regex("""GlassStudy_Filament_(\d{5})\.pupdate""",RegexOption.IGNORE_CASE)
                directory.listFiles()?.asSequence()?.filter { it.isFile }?.mapNotNull { file ->
                    val version=regex.matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull()
                    if(version!=null && version>installed) version to Uri.fromFile(file) else null
                }?.sortedByDescending { it.first }?.map { it.second }?.toList()
                    ?: throw java.io.IOException("Не удалось прочитать Download")
            } catch(error:Exception) {
                lastScanError="Download: "+error.javaClass.simpleName+": "+(error.message ?: "нет доступа")
                emptyList()
            }
        }
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
                        RegexOption.IGNORE_CASE).matchEntire(file) ?: continue
                    val v=match.groupValues[1].toIntOrNull() ?:continue
                    if(v<=installed)continue
                    matches.add(v to DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(id)))
                }
            }
            matches.sortedByDescending { it.first }.map { it.second }
        } catch(error:Exception) {
            Log.w("GlassStudy","Patch folder unavailable; grant can be changed",error)
            lastScanError="${error.javaClass.simpleName}: ${error.message ?: "нет доступа"}"
            // Keep the grant configuration. A transient DocumentsProvider failure
            // should never force the user to select their folder again.
            emptyList()
        }
    }
    fun newestAbove(installed:Int):Uri?=candidatesAbove(installed).firstOrNull()
}
