package io.github.shaonialife.cardeck.util

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object ClusterSampleFile {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

    fun save(context: Context, prefix: String, extension: String, mimeType: String, write: (OutputStream) -> Unit): String {
        val name = "$prefix-${LocalDateTime.now().format(stamp)}.$extension"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/CarDeck-BMW-Cluster")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("无法创建文件")
        var saved = false
        try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("无法写入文件")
            stream.use(write)
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) != 1) throw IOException("发布文件失败")
            saved = true
            return "下载/CarDeck-BMW-Cluster/$name"
        } finally {
            if (!saved) runCatching { resolver.delete(uri, null, null) }
        }
    }
}
