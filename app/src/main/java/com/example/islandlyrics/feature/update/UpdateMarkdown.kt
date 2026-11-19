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

package com.example.islandlyrics.feature.update

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.widget.TextView
import coil3.Image
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.error
import coil3.request.placeholder
import coil3.target.Target
import com.example.islandlyrics.core.cache.AppImageCacheManager
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableLoader
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.DrawableUtils
import io.noties.markwon.image.ImageSpanFactory
import org.commonmark.node.Image as MarkdownImage
import kotlin.math.roundToInt

object UpdateMarkdown {
    fun create(context: Context): Markwon {
        val imageLoader = AppImageCacheManager.getImageLoader(context)
        val imagesPlugin = object : AbstractMarkwonPlugin() {
            private val loader = MarkdownImageLoader(context, imageLoader)

            override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                builder.setFactory(MarkdownImage::class.java, ImageSpanFactory())
            }

            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.asyncDrawableLoader(loader)
            }

            override fun beforeSetText(textView: TextView, markdown: Spanned) {
                AsyncDrawableScheduler.unschedule(textView)
            }

            override fun afterSetText(textView: TextView) {
                AsyncDrawableScheduler.schedule(textView)
            }
        }

        return Markwon.builder(context)
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(imagesPlugin)
            .build()
    }

    // Coil 3 integration follows Markwon's CoilImagesPlugin (Apache-2.0).
    // See THIRD_PARTY_NOTICES.md, section 5.1.
    private class MarkdownImageLoader(
        private val context: Context,
        private val imageLoader: ImageLoader
    ) : AsyncDrawableLoader() {
        private class PendingRequest(var disposable: Disposable? = null)

        private val requests = mutableMapOf<AsyncDrawable, PendingRequest>()

        override fun load(drawable: AsyncDrawable) {
            cancel(drawable)
            val pending = PendingRequest()
            requests[drawable] = pending
            val placeholder = createPlaceholder(context, drawable)
            val request = ImageRequest.Builder(context)
                .data(drawable.destination)
                .placeholder(placeholder)
                .error(placeholder)
                .target(object : Target {
                    override fun onStart(placeholder: Image?) = deliver(placeholder, false)

                    override fun onSuccess(result: Image) = deliver(result, true)

                    override fun onError(error: Image?) = deliver(error, true)

                    private fun deliver(image: Image?, complete: Boolean) {
                        if (requests[drawable] !== pending) return
                        if (complete) requests.remove(drawable)
                        if (image != null && drawable.isAttached) {
                            val result = image.asDrawable(context.resources)
                            DrawableUtils.applyIntrinsicBoundsIfEmpty(result)
                            drawable.setResult(result)
                            requestLayout(drawable)
                        }
                    }
                })
                .build()
            val disposable = imageLoader.enqueue(request)
            // Memory-cache hits can finish before enqueue returns.
            if (requests[drawable] === pending) {
                pending.disposable = disposable
            } else {
                disposable.dispose()
            }
        }

        override fun cancel(drawable: AsyncDrawable) {
            requests.remove(drawable)?.disposable?.dispose()
        }

        override fun placeholder(drawable: AsyncDrawable): Drawable? = null
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
