package com.tamimrijan.yoinks.util

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object Files {
    private fun uri(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun mime(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: if (file.extension.equals("mp3", true)) "audio/mpeg" else "video/*"

    fun open(context: Context, path: String) {
        val f = File(path)
        if (!f.exists()) {
            Toast.makeText(context, "That file was moved or deleted", Toast.LENGTH_SHORT).show(); return
        }
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri(context, f), mime(f))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can play that file", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, path: String) {
        val f = File(path)
        if (!f.exists()) return
        val send = Intent(Intent.ACTION_SEND).setType(mime(f))
            .putExtra(Intent.EXTRA_STREAM, uri(context, f))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openDownloads(context: Context) {
        runCatching {
            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
