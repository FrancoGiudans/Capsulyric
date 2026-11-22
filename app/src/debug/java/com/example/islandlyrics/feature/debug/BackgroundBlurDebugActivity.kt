/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.debug

import android.graphics.Bitmap
import android.graphics.Canvas as BitmapCanvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackIcon
import com.example.islandlyrics.ui.miuix.preference.BlurOverlayDropdownPreference
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.ui.navigation.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import kotlin.math.roundToInt

class BackgroundBlurDebugActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiuixAppTheme {
                BackgroundBlurDebugScreen(onBack = ::finish)
            }
        }
    }
}

private enum class BackgroundBlurMode(@StringRes val label: Int) {
    Orbit(R.string.debug_background_blur_orbit),
    Oklab(R.string.debug_background_blur_oklab),
    Gaussian(R.string.debug_background_blur_gaussian),
    Original(R.string.debug_background_blur_original),
}

@Composable
private fun BackgroundBlurDebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var imageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var mode by rememberSaveable { mutableStateOf(BackgroundBlurMode.Orbit) }
    var followArtwork by rememberSaveable { mutableStateOf(false) }
    var radius by rememberSaveable { mutableFloatStateOf(24f) }
    val chooseImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            imageUri = uri
            followArtwork = false
        }
    }
    val image by produceState<Bitmap?>(null, imageUri) {
        val uri = imageUri
        value = null
        if (uri != null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val size = info.size
                        val maxSide = maxOf(size.width, size.height)
                        if (maxSide > 1440) {
                            val scale = 1440f / maxSide
                            decoder.setTargetSize(
                                (size.width * scale).roundToInt().coerceAtLeast(1),
                                (size.height * scale).roundToInt().coerceAtLeast(1),
                            )
                        }
                    }
                }.getOrNull()
            }
            if (value == null) {
                imageUri = null
                Toast.makeText(context, R.string.debug_background_blur_image_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val liveArtwork = if (followArtwork) {
        val repo = remember { LyricRepository.getInstance() }
        val art by repo.liveTrackAlbumArt.observeAsState()
        art?.bitmap?.takeUnless(Bitmap::isRecycled)
    } else {
        null
    }
    val source = if (followArtwork) liveArtwork else image
    val sampleImage = remember { makeTestPattern() }
    val previewImage = source ?: sampleImage
    var orbitCover by remember(previewImage) { mutableStateOf<DebugOrbitCover?>(null) }
    var haloPalette by remember(previewImage) { mutableStateOf<List<Color>?>(null) }
    LaunchedEffect(previewImage, mode) {
        when {
            mode == BackgroundBlurMode.Orbit && orbitCover == null -> {
                orbitCover = withContext(Dispatchers.Default) { prepareDebugOrbitCover(previewImage) }
            }
            mode == BackgroundBlurMode.Oklab && haloPalette == null -> {
                haloPalette = withContext(Dispatchers.Default) { prepareDebugHaloPalette(previewImage) }
            }
            else -> Unit
        }
    }
    val preview: @Composable (Modifier) -> Unit = { modifier ->
        val cover = orbitCover
        val colors = haloPalette
        val previewModifier = modifier.clip(RoundedCornerShape(16.dp))
        when {
            mode == BackgroundBlurMode.Orbit && cover != null -> DebugOrbitFlow(cover, previewModifier)
            mode == BackgroundBlurMode.Oklab && colors != null -> DebugOklabHalo(colors, previewModifier)
            else -> BackgroundBlurPreview(
                image = previewImage,
                radius = if (mode == BackgroundBlurMode.Gaussian) radius.dp else 0.dp,
                contentScale = if (source == null) ContentScale.FillBounds else ContentScale.Crop,
                modifier = previewModifier,
            )
        }
    }
    val controls: @Composable () -> Unit = {
        Card(Modifier.fillMaxWidth()) {
            BlurOverlayDropdownPreference(
                title = stringResource(R.string.debug_background_blur_mode),
                items = BackgroundBlurMode.entries.map { stringResource(it.label) },
                selectedIndex = mode.ordinal,
                onSelectedIndexChange = { mode = BackgroundBlurMode.entries[it] },
            )
            if (mode == BackgroundBlurMode.Gaussian) {
                SliderPreference(
                    title = stringResource(R.string.debug_background_blur_radius),
                    value = radius,
                    valueText = stringResource(R.string.debug_background_blur_radius_value, radius.roundToInt()),
                    valueRange = 0f..80f,
                    onValueChange = { radius = it },
                )
            }
            SwitchPreference(
                title = stringResource(R.string.debug_background_blur_follow_cover),
                checked = followArtwork,
                onCheckedChange = { followArtwork = it },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.debug_background_blur_choose_image),
                    onClick = { chooseImage.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.debug_background_blur_test_image),
                    onClick = {
                        imageUri = null
                        followArtwork = false
                    },
                    modifier = Modifier.weight(1f),
                    enabled = imageUri != null || followArtwork,
                )
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.debug_background_blur_title),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        MiuixBackIcon(contentDescription = stringResource(R.string.online_lyric_debug_back))
                    }
                },
            )
        },
    ) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets).padding(12.dp)) {
            if (maxWidth > maxHeight) {
                val controlsWidth = minOf(320.dp, maxWidth * 0.47f)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    preview(Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.width(controlsWidth).verticalScroll(rememberScrollState())) {
                        controls()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    preview(Modifier.weight(1f).fillMaxWidth())
                    controls()
                }
            }
        }
    }
}

@Composable
private fun BackgroundBlurPreview(image: Bitmap, radius: Dp, contentScale: ContentScale, modifier: Modifier) {
    Box(modifier) {
        Image(
            bitmap = remember(image) { image.asImageBitmap() },
            contentDescription = null,
            modifier = Modifier.matchParentSize().graphicsLayer {
                val radiusPx = radius.toPx()
                renderEffect = if (radiusPx > 0f) BlurEffect(radiusPx, radiusPx, TileMode.Clamp) else null
            },
            contentScale = contentScale,
        )
    }
}

private fun makeTestPattern(): Bitmap {
    val size = 360
    val image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = BitmapCanvas(image)
    val paint = Paint()
    val bandWidth = size / 3f
    val mid = size / 2f
    val bands = intArrayOf(0xFFE83860.toInt(), 0xFFFFC048.toInt(), 0xFF3169F5.toInt())
    bands.forEachIndexed { index, color ->
        paint.color = color
        canvas.drawRect(index * bandWidth, 0f, (index + 1) * bandWidth, mid, paint)
    }
    val cell = size / 12f
    for (row in 0 until 6) {
        for (column in 0 until 12) {
            paint.color = if ((row + column) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF161616.toInt()
            canvas.drawRect(column * cell, mid + row * cell, (column + 1) * cell, mid + (row + 1) * cell, paint)
        }
    }
    return image
}
