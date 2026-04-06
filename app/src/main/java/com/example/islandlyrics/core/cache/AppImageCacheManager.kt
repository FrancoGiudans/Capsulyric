package com.example.islandlyrics.core.cache

import android.content.Context
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import java.io.File

object AppImageCacheManager {

    data class ImageCacheStats(
        val fileCount: Int = 0,
        val totalBytes: Long = 0L,
        val lastUpdatedAt: Long? = null
    )

    private const val UPDATE_IMAGE_CACHE_DIR = "update_markdown_images"
    @Volatile
    private var imageLoader: ImageLoader? = null

    fun getImageLoader(context: Context): ImageLoader {
        val appContext = context.applicationContext
        return imageLoader ?: synchronized(this) {
            imageLoader ?: ImageLoader.Builder(appContext)
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDirectory(appContext))
                        .maxSizeBytes(64L * 1024L * 1024L)
                        .build()
                }
                .respectCacheHeaders(false)
                .build()
                .also { imageLoader = it }
        }
    }

    fun getStats(context: Context): ImageCacheStats {
        val dir = cacheDirectory(context.applicationContext)
        if (!dir.exists()) return ImageCacheStats()

        var fileCount = 0
        var totalBytes = 0L
        var lastUpdatedAt: Long? = null
        dir.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            fileCount += 1
            totalBytes += file.length()
            lastUpdatedAt = maxOf(lastUpdatedAt ?: 0L, file.lastModified())
        }
        return ImageCacheStats(fileCount, totalBytes, lastUpdatedAt?.takeIf { it > 0L })
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clear(context: Context) {
        val appContext = context.applicationContext
        synchronized(this) {
            imageLoader?.memoryCache?.clear()
            imageLoader?.diskCache?.clear()
        }
        cacheDirectory(appContext).deleteRecursively()
        cacheDirectory(appContext).mkdirs()
    }

    private fun cacheDirectory(context: Context): File {
        return File(context.cacheDir, UPDATE_IMAGE_CACHE_DIR)
    }
}
