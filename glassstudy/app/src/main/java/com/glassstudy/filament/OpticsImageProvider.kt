package com.glassstudy.filament

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Read-only, exact-path provider for a clipboard URI; no storage permission. */
class OpticsImageProvider:ContentProvider() {
    override fun onCreate()=true
    private fun image(uri:Uri):File {
        require(uri.path=="/image.png") { "Unknown image" }
        return File(requireNotNull(context).filesDir,"optics-test/image.png")
    }
    override fun getType(uri:Uri):String { image(uri);return "image/png" }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        require(mode=="r") { "Read only" }
        return ParcelFileDescriptor.open(image(uri),ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        val file=image(uri)
        val columns=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(columns).apply { addRow(columns.map { when(it) {
            OpenableColumns.DISPLAY_NAME -> "GlassStudy-test.png"
            OpenableColumns.SIZE -> file.length()
            else -> null
        } }.toTypedArray()) }
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
}
