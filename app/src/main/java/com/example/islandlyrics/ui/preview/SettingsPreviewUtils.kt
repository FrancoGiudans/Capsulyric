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

package com.example.islandlyrics.ui.preview

import android.graphics.Bitmap
import com.example.islandlyrics.R
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.overlay.config.OneUiCapsuleColorMode
import com.example.islandlyrics.ui.overlay.config.OverlayRenderDefaults
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandColorSource
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandLyricLayout
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandSecondaryTextMode
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette

@Composable
fun MetricCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun CapsulePreview(
    dynamicIconEnabled: Boolean,
    iconStyle: String,
    oneuiCapsuleColorMode: String = OneUiCapsuleColorMode.BLACK,
    superIslandEnabled: Boolean = false,
    superIslandLyricMode: String = "standard",
    superIslandFullLyricShowLeftCover: Boolean = true,
    superIslandStandardShowLeftCover: Boolean = true,
    superIslandColorSource: String = SuperIslandColorSource.ALBUM_ART,
    superIslandCustomColor: Color = Color(0xFF3482FF),
    superIslandSmartMinContrast: Float = SuperIslandColorSource.SMART_CONTRAST_DEFAULT,
    superIslandSmartWhiteRatio: Float = SuperIslandColorSource.SMART_WHITE_RATIO_DEFAULT,
    showBorder: Boolean = false
) {
    val repo = remember { LyricRepository.getInstance() }
    val metadata by repo.liveMetadata.observeAsState()
    val lyricInfo by repo.liveLyric.observeAsState()
    val albumArt by repo.liveAlbumArt.observeAsState()

    val title = metadata?.title ?: "Song Title"
    val artist = metadata?.artist ?: "Artist Name"
    val currentLyric = lyricInfo?.lyric ?: "Lyrics waiting..."
    val titleWithArtist = if (artist.isNotBlank()) "$title - $artist" else title
    
    // Extract Color for OneUI
    var extractedColor by remember { mutableStateOf<Color?>(null) }
    
    LaunchedEffect(albumArt) {
        if (albumArt != null) {
            Palette.from(albumArt!!).generate { palette ->
                if (palette != null) {
                    val color = palette.getVibrantColor(
                        palette.getMutedColor(
                            palette.getDominantColor(android.graphics.Color.BLACK)
                        )
                    )
                    extractedColor = Color(color)
                }
            }
        } else {
            extractedColor = null
        }
    }
    
    val pillHeight = 56.dp // Standard Island height simulation
    val previewAlbumColor = extractedColor?.toArgb() ?: Color.Black.toArgb()
    val pillColor = Color(
        OneUiCapsuleColorMode.resolveColor(
            mode = oneuiCapsuleColorMode,
            albumColor = previewAlbumColor
        )
    )
    
    val superIslandAccentColor = Color(
        SuperIslandColorSource.resolveColor(
            source = superIslandColorSource,
            albumColor = extractedColor?.toArgb() ?: OverlayRenderDefaults.COLOR_PRIMARY,
            customColor = superIslandCustomColor.toArgb(),
            smartMinimumContrast = superIslandSmartMinContrast,
            smartWhiteRatio = superIslandSmartWhiteRatio
        )
    )
    val superIslandColorized = SuperIslandColorSource.isColorized(superIslandColorSource)

    if (superIslandEnabled) {
        val showLeftCover = albumArt != null && when (superIslandLyricMode) {
            "full" -> superIslandFullLyricShowLeftCover
            else -> superIslandStandardShowLeftCover
        }
        val split = SuperIslandLyricLayout.splitFullLyric(currentLyric, showLeftCover)
        val leftText = when (superIslandLyricMode) {
            "full" -> split.left.ifEmpty { "♪" }
            else -> SuperIslandLyricLayout.takeByWeight(
                titleWithArtist.ifBlank { "♪" },
                if (showLeftCover) 13 else 16
            ).ifEmpty { "♪" }
        }
        val rightText = when (superIslandLyricMode) {
            "full" -> split.right.ifEmpty { "♪" }
            else -> SuperIslandLyricLayout.takeByWeight(currentLyric.ifBlank { "♪" }, 14).ifEmpty { "♪" }
        }
        val leftAlbumArt = if (showLeftCover) albumArt else null

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(pillHeight + 16.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .then(
                        if (showBorder) {
                            Modifier.border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leftAlbumArt != null) {
                    Image(
                        bitmap = leftAlbumArt.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = leftText,
                    color = if (superIslandColorized) superIslandAccentColor else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(if (showLeftCover) 1.12f else 1.22f)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = rightText,
                    color = if (superIslandColorized) superIslandAccentColor else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(pillHeight + 16.dp)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pillHeight)
                    .clip(CircleShape)
                    .background(pillColor)
                    .then(
                        if (showBorder) {
                            Modifier.border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                if (dynamicIconEnabled) {
                    if (albumArt != null) {
                        Image(
                            bitmap = albumArt!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.widthIn(max = 120.dp)
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = currentLyric,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SuperIslandPreviewPill(
    text: String,
    albumArt: Bitmap?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
@Composable
private fun SuperIslandCoverWithBadge(
    albumArt: Bitmap?,
    appIcon: android.graphics.drawable.Drawable?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 54.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    badgeSize: androidx.compose.ui.unit.Dp = 20.dp
) {
    Box(modifier = modifier.size(size)) {
        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.5f),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Gray)
                )
            }
        }

        // App Badge (Playing App Icon)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .size(badgeSize)
                .clip(CircleShape)
                .background(Color(0xFF0C0C0C))
                .padding(1.5.dp)
        ) {
            if (appIcon != null) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { context ->
                        android.widget.ImageView(context).apply {
                            setImageDrawable(appIcon)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun NotificationPreview(
    progressColorEnabled: Boolean,
    actionStyle: String,
    superIslandEnabled: Boolean = false,
    superIslandColorSource: String = SuperIslandColorSource.OFF,
    superIslandCustomColor: Color = Color(0xFF3482FF),
    superIslandSmartMinContrast: Float = SuperIslandColorSource.SMART_CONTRAST_DEFAULT,
    superIslandSmartWhiteRatio: Float = SuperIslandColorSource.SMART_WHITE_RATIO_DEFAULT,
    superIslandMediaButtonLayout: String = "two_button",
    superIslandNotificationStyle: String = "standard",
    superIslandLyricMode: String = "standard",
    superIslandFullLyricShowLeftCover: Boolean = true,
    showProgressBar: Boolean = true,
    template2PicSource: String = "album_art",
    showBorder: Boolean = false,
    superIslandSecondaryTextModes: List<String> = emptyList()
) {
    val context = LocalContext.current
    val repo = remember { LyricRepository.getInstance() }
    val metadata by repo.liveMetadata.observeAsState()
    val lyricInfo by repo.liveLyric.observeAsState()
    val progressInfo by repo.liveProgress.observeAsState()
    val albumArt by repo.liveAlbumArt.observeAsState()

    val title = metadata?.title ?: "打上花火"
    val artist = metadata?.artist ?: "DAOKO×米津玄師"
    val isLiveLyric = lyricInfo != null
    val currentLyric = lyricInfo?.lyric ?: when {
        actionStyle == "template2" -> "ひそかに二人を見ていた"
        superIslandMediaButtonLayout == "three_button" -> "砂の上 刻んだ足跡"
        superIslandMediaButtonLayout == "two_button" -> "あの日見渡した海岸通り"
        else -> "この夜が続いて欲しかった"
    }
    val sourceApp = lyricInfo?.sourceApp ?: "Source App"
    val effectiveButtonLayout = if (superIslandNotificationStyle == "advanced_beta" || superIslandNotificationStyle == "advanced_lyrics_dual") "three_button" else superIslandMediaButtonLayout
    val notificationLyric = when {
        superIslandEnabled && actionStyle == "media_controls" && effectiveButtonLayout == "three_button" ->
            SuperIslandLyricLayout.takeByWeight(currentLyric, 10).ifEmpty { currentLyric }
        superIslandEnabled && actionStyle == "media_controls" && effectiveButtonLayout == "two_button" ->
            SuperIslandLyricLayout.takeByWeight(currentLyric, 14).ifEmpty { currentLyric }
        else -> currentLyric
    }
    
    val position = progressInfo?.position ?: 0L
    val duration = progressInfo?.duration ?: 100L
    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0.65f

    // Calculate Color
    var extractedColor by remember { mutableStateOf<Color?>(null) }
    
    LaunchedEffect(albumArt, metadata) {
        if (albumArt != null) {
            Palette.from(albumArt!!).generate { palette ->
                val vibrant = palette?.vibrantSwatch?.rgb
                if (vibrant != null) {
                    extractedColor = Color(vibrant)
                } else {
                    val dominant = palette?.dominantSwatch?.rgb
                    if (dominant != null) extractedColor = Color(dominant)
                }
            }
        } else if (metadata?.packageName != null) {
            // Fallback to app icon color in preview
            try {
                val icon = context.packageManager.getApplicationIcon(metadata!!.packageName)
                val bitmap = if (icon is android.graphics.drawable.BitmapDrawable) {
                    icon.bitmap
                } else {
                    val b = android.graphics.Bitmap.createBitmap(icon.intrinsicWidth, icon.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(b)
                    icon.setBounds(0, 0, canvas.width, canvas.height)
                    icon.draw(canvas)
                    b
                }
                Palette.from(bitmap).generate { palette ->
                    val vibrant = palette?.vibrantSwatch?.rgb
                    if (vibrant != null) {
                        extractedColor = Color(vibrant)
                    } else {
                        val dominant = palette?.dominantSwatch?.rgb
                        if (dominant != null) extractedColor = Color(dominant)
                    }
                }
            } catch (e: Exception) {
                extractedColor = null
            }
        } else {
            extractedColor = null
        }
    }
    
    val barColor = if (progressColorEnabled && extractedColor != null) extractedColor!! else Color(0xFF3482FF)
    val superIslandAccentColor = Color(
        SuperIslandColorSource.resolveColor(
            source = superIslandColorSource,
            albumColor = extractedColor?.toArgb() ?: OverlayRenderDefaults.COLOR_PRIMARY,
            customColor = superIslandCustomColor.toArgb(),
            smartMinimumContrast = superIslandSmartMinContrast,
            smartWhiteRatio = superIslandSmartWhiteRatio
        )
    )
    val superIslandColorized = SuperIslandColorSource.isColorized(superIslandColorSource)
    val textColor = if (superIslandEnabled && superIslandColorized) superIslandAccentColor else Color.White
    val secondaryTextColor = if (superIslandEnabled && superIslandColorized) {
        superIslandAccentColor.copy(alpha = 0.8f)
    } else {
        Color(0xFFB0B0B0)
    }

    // App Badge (Playing App Icon)
    val appIcon = remember(metadata?.packageName) {
        try {
            metadata?.packageName?.let { context.packageManager.getApplicationIcon(it) }
        } catch (e: Exception) {
            null
        }
    }

    if (superIslandEnabled) {
        // Premium Super Island Style Notification Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0C0C0C)) // Xiaomi Island Dark
                .then(
                    if (showBorder) {
                        Modifier.border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
                    } else {
                        Modifier
                    }
                )
                .padding(16.dp)
        ) {
            if (actionStyle == "media_controls") {
                val showAdvancedStyle = superIslandNotificationStyle == "advanced_beta" || superIslandNotificationStyle == "advanced_lyrics_dual"
                val showPrevButton = if (showAdvancedStyle) true else superIslandMediaButtonLayout == "three_button"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showAdvancedStyle) {
                        // Advanced / Beta circular album art style
                        Box(modifier = Modifier.size(52.dp)) {
                            if (albumArt != null) {
                                Image(
                                    bitmap = albumArt!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.DarkGray)
                                )
                            }
                        }
                    } else {
                        // Standard Super Island: Rounded Square Cover with App Badge
                        SuperIslandCoverWithBadge(
                            albumArt = albumArt,
                            appIcon = appIcon,
                            size = 54.dp,
                            shape = RoundedCornerShape(14.dp),
                            badgeSize = 20.dp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Middle: Text Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notificationLyric,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (artist.isNotBlank()) "$title - $artist" else title,
                            color = secondaryTextColor,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right Group: Playback Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showPrevButton) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_previous),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        val isPlaying = repo.isPlaying.value ?: true

                        if (showAdvancedStyle) {
                            // Play/Pause with Progress Ring for Advanced Styles
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(0.85f)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                )
                                CircularProgressIndicator(
                                    progress = { progress },
                                    strokeWidth = 3.dp,
                                    color = if (superIslandColorized) superIslandAccentColor else Color.White,
                                    trackColor = Color.White.copy(alpha = 0.18f),
                                    modifier = Modifier.fillMaxSize()
                                )
                                Icon(
                                    painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            // Standard Super Island: Clean Vector Play/Pause Icon
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Next Button
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_next),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            } else if (actionStyle == "template2") {
                // 模板2（文本组件2 + 识别图形组件1）：无按钮、无进度条
                val secondaryText = remember(lyricInfo, metadata, superIslandSecondaryTextModes) {
                    val modes = superIslandSecondaryTextModes.mapNotNull { SuperIslandSecondaryTextMode.from(it) }
                        .ifEmpty { listOf(SuperIslandSecondaryTextMode.TRANSLATION) }

                    if (isLiveLyric) {
                        var foundText: String? = null
                        for (mode in modes) {
                            foundText = when (mode) {
                                SuperIslandSecondaryTextMode.TRANSLATION -> lyricInfo?.translation?.takeIf { it.isNotBlank() }
                                SuperIslandSecondaryTextMode.ROMANIZATION -> lyricInfo?.roma?.takeIf { it.isNotBlank() }
                                SuperIslandSecondaryTextMode.NEXT_LYRIC -> null
                            }
                            if (foundText != null) break
                        }
                        foundText ?: if (artist.isNotBlank()) "$title - $artist" else title
                    } else {
                        when (modes.firstOrNull()) {
                            SuperIslandSecondaryTextMode.ROMANIZATION -> "hisoka ni futari o miteita"
                            SuperIslandSecondaryTextMode.NEXT_LYRIC -> "海岸通り 通り過ぎてゆく"
                            SuperIslandSecondaryTextMode.TRANSLATION -> "正偷偷窥探着我们"
                            else -> "正偷偷窥探着我们"
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentLyric,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = secondaryText,
                            color = secondaryTextColor,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2A2A2A))
                    ) {
                        when (template2PicSource) {
                            "playing_app", "app_icon" -> {
                                val iconPackage = if (template2PicSource == "app_icon") {
                                    context.packageName
                                } else {
                                    metadata?.packageName
                                }
                                val template2Icon = remember(iconPackage) {
                                    try {
                                        iconPackage?.let { context.packageManager.getApplicationIcon(it) }
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (template2Icon != null) {
                                    androidx.compose.ui.viewinterop.AndroidView(
                                        factory = { ctx ->
                                            android.widget.ImageView(ctx).apply {
                                                setImageDrawable(template2Icon)
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            "custom" -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "CUSTOM",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            else -> {
                                if (albumArt != null) {
                                    Image(
                                        bitmap = albumArt!!.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 1.png: Standard Super Island (No buttons + Show progress bar)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Rounded Square Album Art + App Badge
                        SuperIslandCoverWithBadge(
                            albumArt = albumArt,
                            appIcon = appIcon,
                            size = 54.dp,
                            shape = RoundedCornerShape(14.dp),
                            badgeSize = 20.dp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        // Middle: Metadata
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (superIslandLyricMode) {
                                    "full" -> {
                                        val split = SuperIslandLyricLayout.splitFullLyric(
                                            currentLyric,
                                            albumArt != null && superIslandFullLyricShowLeftCover
                                        )
                                        "${split.left} ${split.right}".trim().ifEmpty { currentLyric }
                                    }
                                    else -> currentLyric
                                },
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (artist.isNotBlank()) "$title - $artist" else title,
                                color = secondaryTextColor,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Bottom: Progress Bar
                    if (showProgressBar) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (superIslandColorized) superIslandAccentColor else barColor,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }
    } else {
        // Standard Android Notification Style Card (Legacy)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF202124)) // Dark notification background
                .then(
                    if (showBorder) {
                        Modifier.border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                    } else {
                        Modifier
                    }
                )
                .padding(20.dp)
        ) {
            Column {
                // HEADER: [Icon] App Name • Source • now
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // App Icon Simulation
                    Box(modifier = Modifier.size(20.dp).clip(CircleShape)) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_background),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val pm = context.packageManager
                    val appName = remember { context.applicationInfo.loadLabel(pm).toString() }

                    Text(
                        text = "$appName • $sourceApp • now",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // CONTENT: Title - Artist
                Text(
                    text = "$title - $artist",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // LYRIC
                Text(
                    text = currentLyric,
                    color = Color.White, // High contrast
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // PROGRESS BAR
                if (showProgressBar) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = barColor,
                        trackColor = Color(0xFF454545) // Darker gray track
                    )
                }

                // ACTIONS
                if (actionStyle != "disabled") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        if (actionStyle == "miplay") {
                            TextButton(onClick = {}) {
                                Text("Mi Play", color = Color(0xFF5E97F6), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }
                        } else {
                            // Standard Controls
                            TextButton(
                                onClick = {},
                                contentPadding = PaddingValues(horizontal = 0.dp)
                            ) {
                                Text("Pause", color = Color(0xFF8AB4F8), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            TextButton(
                                onClick = {},
                                contentPadding = PaddingValues(horizontal = 0.dp)
                            ) {
                                Text("Next", color = Color(0xFF8AB4F8), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
