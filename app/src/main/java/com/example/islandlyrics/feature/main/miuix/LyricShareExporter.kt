/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

internal object LyricShareExporter {
    private const val Directory = "shared_lyrics"
    private const val MimeType = "image/png"
    private const val CacheLifetimeMillis = 24 * 60 * 60 * 1000L

    suspend fun share(context: Context, card: LyricShareCard) {
        val file = renderAndWrite(card) { bitmap ->
            val directory = File(context.cacheDir, Directory)
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Unable to create lyric share directory")
            }
            val now = System.currentTimeMillis()
            directory.listFiles()?.forEach {
                if (it.isFile && it.extension == "png" && now - it.lastModified() > CacheLifetimeMillis) it.delete()
            }
            val output = File.createTempFile("Capsulyric-", ".png", directory)
            try {
                output.outputStream().use { bitmap.writePng(it) }
                output
            } catch (error: Throwable) {
                output.delete()
                throw error
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(card.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    suspend fun save(context: Context, card: LyricShareCard) {
        renderAndWrite(card) { bitmap ->
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Capsulyric-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, MimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Capsulyric")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Unable to create gallery entry")
            try {
                val output = resolver.openOutputStream(uri) ?: throw IOException("Unable to open gallery entry")
                output.use { bitmap.writePng(it) }
                val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                if (resolver.update(uri, ready, null, null) == 0) throw IOException("Unable to publish gallery entry")
            } catch (error: Throwable) {
                runCatching { resolver.delete(uri, null, null) }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }
    }

    private suspend fun <T> renderAndWrite(card: LyricShareCard, write: (Bitmap) -> T): T =
        withContext(Dispatchers.Default) {
            val bitmap = LyricShareCardRenderer.render(card)
            try {
                withContext(Dispatchers.IO) { write(bitmap) }
            } finally {
                bitmap.recycle()
            }
        }

    private fun Bitmap.writePng(output: OutputStream) {
        if (!compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("Unable to encode PNG")
    }
}
