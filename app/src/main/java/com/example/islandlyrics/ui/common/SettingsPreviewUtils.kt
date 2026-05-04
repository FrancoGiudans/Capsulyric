package com.example.islandlyrics.ui.common

import android.graphics.Bitmap
import com.example.islandlyrics.R
import com.example.islandlyrics.data.LyricRepository
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
    superIslandFullLyricShowLeftCover: Boolean = true
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
    
    if (superIslandEnabled) {
        val showLeftCover = albumArt != null && (superIslandLyricMode != "full" || superIslandFullLyricShowLeftCover)
        val split = SuperIslandLyricLayout.splitFullLyric(currentLyric, showLeftCover)
        val leftText = if (superIslandLyricMode == "full") {
            split.left.ifEmpty { "♪" }
        } else {
            SuperIslandLyricLayout.takeByWeight(
                titleWithArtist.ifBlank { "♪" },
                if (showLeftCover) 13 else 16
            ).ifEmpty { "♪" }
        }
        val rightText = if (superIslandLyricMode == "full") {
            split.right.ifEmpty { "♪" }
        } else {
            SuperIslandLyricLayout.takeByWeight(currentLyric.ifBlank { "♪" }, 14).ifEmpty { "♪" }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(pillHeight + 16.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.weight(if (showLeftCover) 1.12f else 1.22f)) {
                SuperIslandPreviewPill(
                    text = leftText,
                    albumArt = if (showLeftCover) albumArt else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                SuperIslandPreviewPill(
                    text = rightText,
                    albumArt = null,
                    modifier = Modifier.fillMaxWidth(0.92f)
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
fun NotificationPreview(
    progressColorEnabled: Boolean,
    actionStyle: String,
    superIslandEnabled: Boolean = false,
    superIslandTextColorEnabled: Boolean = false,
    superIslandMediaButtonLayout: String = "two_button",
    superIslandNotificationStyle: String = "standard",
    superIslandLyricMode: String = "standard",
    superIslandFullLyricShowLeftCover: Boolean = true
) {
    val context = LocalContext.current
    val repo = remember { LyricRepository.getInstance() }
    val metadata by repo.liveMetadata.observeAsState()
    val lyricInfo by repo.liveLyric.observeAsState()
    val progressInfo by repo.liveProgress.observeAsState()
    val albumArt by repo.liveAlbumArt.observeAsState()

    val title = metadata?.title ?: "Song Title"
    val artist = metadata?.artist ?: "Artist Name"
    val currentLyric = lyricInfo?.lyric ?: "Lyrics will appear here..."
    val sourceApp = lyricInfo?.sourceApp ?: "Source App"
    val effectiveButtonLayout = if (superIslandNotificationStyle == "advanced_beta") "three_button" else superIslandMediaButtonLayout
    val notificationLyric = when {
        superIslandEnabled && actionStyle == "media_controls" && effectiveButtonLayout == "three_button" ->
            SuperIslandLyricLayout.takeByWeight(currentLyric, 10).ifEmpty { currentLyric }
        superIslandEnabled && actionStyle == "media_controls" && effectiveButtonLayout == "two_button" ->
            SuperIslandLyricLayout.takeByWeight(currentLyric, 14).ifEmpty { currentLyric }
        else -> currentLyric
    }
    
    val position = progressInfo?.position ?: 0L
    val duration = progressInfo?.duration ?: 100L
    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0.3f // Default 30% if no info

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
    
    val barColor = if (progressColorEnabled && extractedColor != null) extractedColor!! else MaterialTheme.colorScheme.primary
    val textColor = if (superIslandTextColorEnabled && extractedColor != null) extractedColor!! else Color.White
    val secondaryTextColor = if (superIslandTextColorEnabled && extractedColor != null) extractedColor!!.copy(alpha = 0.8f) else Color(0xFFB0B0B0)

    if (superIslandEnabled) {
        // Premium Super Island Style Notification Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0C0C0C)) // Xiaomi Island Dark
                .padding(16.dp)
        ) {
            if (actionStyle == "media_controls") {
                val showAdvancedStyle = superIslandNotificationStyle == "advanced_beta"
                val showPrevButton = if (showAdvancedStyle) true else superIslandMediaButtonLayout == "three_button"
                // Template 12 Layout: [Album Art] [Lyrics / Title-Artist] [Buttons Group]
                // All on the same row, reflecting real clipping/blocking behavior
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Circular Album Art
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

                    Spacer(modifier = Modifier.width(12.dp))

                    // Middle: Text Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notificationLyric,
                            color = Color.White, // Always white as per user request
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (artist.isNotBlank()) "$title - $artist" else title,
                            color = Color(0xFFB0B0B0), // Standard gray
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
                                modifier = Modifier.size(28.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Play/Pause with Progress Ring
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(44.dp)
                        ) {
                            if (!showAdvancedStyle) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(0.85f)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                )
                            }
                            CircularProgressIndicator(
                                progress = { progress },
                                strokeWidth = 3.dp,
                                color = if (progressColorEnabled) barColor else Color.White,
                                trackColor = if (showAdvancedStyle) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier.fillMaxSize()
                            )
                            val isPlaying = repo.isPlaying.value ?: false
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Next Button
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_next),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                // Template 7 Style (Legacy/Standard)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Album Art + Badge
                        Box(modifier = Modifier.size(64.dp)) {
                            if (albumArt != null) {
                                Image(
                                    bitmap = albumArt!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(28.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(Color.DarkGray)
                                )
                            }

                            // App Badge (Playing App Icon)
                            val appIcon = remember(metadata?.packageName) {
                                try {
                                    metadata?.packageName?.let { context.packageManager.getApplicationIcon(it) }
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 2.dp, y = 2.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0C0C0C))
                                    .padding(2.dp)
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

                        Spacer(modifier = Modifier.width(16.dp))

                        // Middle: Metadata
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (superIslandLyricMode == "full") {
                                    val split = SuperIslandLyricLayout.splitFullLyric(
                                        currentLyric,
                                        albumArt != null && superIslandFullLyricShowLeftCover
                                    )
                                    "${split.left} ${split.right}".trim().ifEmpty { currentLyric }
                                } else {
                                    currentLyric
                                },
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (artist.isNotBlank()) "$title - $artist" else title,
                                color = secondaryTextColor,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Right: Removed icon to match Template 21
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom: Progress Bar
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (progressColorEnabled) barColor else Color.Gray.copy(alpha = 0.6f),
                        trackColor = if (progressColorEnabled) barColor.copy(alpha = 0.2f) else Color(0xFF1A2633)
                    )
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

                Spacer(modifier = Modifier.height(16.dp))

                // PROGRESS BAR
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = barColor,
                    trackColor = Color(0xFF454545) // Darker gray track
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ACTIONS
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
