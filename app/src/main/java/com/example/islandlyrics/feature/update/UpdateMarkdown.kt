package com.example.islandlyrics.feature.update

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.widget.TextView
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.islandlyrics.core.cache.AppImageCacheManager
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.coil.CoilImagesPlugin
import kotlin.math.roundToInt

object UpdateMarkdown {
    fun create(context: Context): Markwon {
        val imageLoader = AppImageCacheManager.getImageLoader(context)
        val coilStore = object : CoilImagesPlugin.CoilStore {
            override fun load(drawable: AsyncDrawable): ImageRequest {
                val placeholder = createPlaceholder(context, drawable)
                return ImageRequest.Builder(context)
                    .data(drawable.destination)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .listener(object : ImageRequest.Listener {
                        override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                            requestLayout(drawable)
                        }

                        override fun onError(request: ImageRequest, result: ErrorResult) {
                            requestLayout(drawable)
                        }
                    })
                    .build()
            }

            override fun cancel(disposable: coil.request.Disposable) {
                disposable.dispose()
            }
        }

        return Markwon.builder(context)
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(CoilImagesPlugin.create(coilStore, imageLoader))
            .build()
    }

    private fun requestLayout(drawable: AsyncDrawable) {
        val callback = drawable.callback
        if (callback is TextView) {
            callback.post { callback.requestLayout() }
        }
    }

    private fun createPlaceholder(context: Context, drawable: AsyncDrawable): Drawable? {
        val width = resolvePlaceholderWidth(context, drawable)
        if (width <= 0) return null

        val density = context.resources.displayMetrics.density
        val minHeight = (120 * density).roundToInt()
        val maxHeight = (480 * density).roundToInt()
        val height = resolvePlaceholderHeight(
            context = context,
            drawable = drawable,
            width = width,
            minHeight = minHeight,
            maxHeight = maxHeight
        )

        return FixedSizeColorDrawable(0x1A000000, width, height)
    }

    private fun resolvePlaceholderHeight(
        context: Context,
        drawable: AsyncDrawable,
        width: Int,
        minHeight: Int,
        maxHeight: Int
    ): Int {
        val imageSize = drawable.imageSize ?: return defaultHeight(width, minHeight, maxHeight)
        val textSize = resolveTextSize(drawable, context)
        val canvasWidth = resolvePlaceholderWidth(context, drawable)

        val widthPx = imageSize.width?.let { resolveDimension(it, canvasWidth, textSize) }
        val heightPx = imageSize.height?.let { resolveDimension(it, canvasWidth, textSize) }

        val resolved = when {
            widthPx != null && heightPx != null -> heightPx
            widthPx != null -> (widthPx * 9f / 16f).roundToInt()
            heightPx != null -> heightPx
            else -> defaultHeight(width, minHeight, maxHeight)
        }
        return resolved.coerceIn(minHeight, maxHeight)
    }

    private fun resolveTextSize(drawable: AsyncDrawable, context: Context): Float {
        val callback = drawable.callback
        if (callback is TextView) {
            return callback.textSize
        }
        val lastKnown = drawable.lastKnowTextSize
        if (lastKnown > 0f) return lastKnown
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            14f,
            context.resources.displayMetrics
        )
    }

    private fun resolveDimension(
        dimension: io.noties.markwon.image.ImageSize.Dimension,
        canvasWidth: Int,
        textSize: Float
    ): Int {
        val unit = dimension.unit
        return when (unit) {
            "%" -> (canvasWidth * (dimension.value / 100f)).roundToInt()
            "em" -> (dimension.value * textSize).roundToInt()
            else -> dimension.value.roundToInt()
        }
    }

    private fun defaultHeight(width: Int, minHeight: Int, maxHeight: Int): Int {
        return (width * 9f / 16f).roundToInt().coerceIn(minHeight, maxHeight)
    }

    private fun resolvePlaceholderWidth(context: Context, drawable: AsyncDrawable): Int {
        val callback = drawable.callback
        if (callback is TextView) {
            val width = callback.width - callback.paddingLeft - callback.paddingRight
            if (width > 0) return width
        }

        val canvasWidth = drawable.lastKnownCanvasWidth
        if (canvasWidth > 0) return canvasWidth

        val density = context.resources.displayMetrics.density
        val fallbackPadding = (32 * density).roundToInt()
        val screenWidth = context.resources.displayMetrics.widthPixels
        return (screenWidth - fallbackPadding).coerceAtLeast(1)
    }

    private class FixedSizeColorDrawable(
        color: Int,
        private val width: Int,
        private val height: Int
    ) : ColorDrawable(color) {
        override fun getIntrinsicWidth(): Int = width
        override fun getIntrinsicHeight(): Int = height
    }
}
