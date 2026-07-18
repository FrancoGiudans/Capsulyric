/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.core.cache

import android.content.Context
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.memory.MemoryCache
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
                .memoryCache {
                    MemoryCache.Builder(appContext)
                        .maxSizeBytes(16 * 1024 * 1024)  // 16 MB — sufficient for markdown images
                        .build()
                }
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
