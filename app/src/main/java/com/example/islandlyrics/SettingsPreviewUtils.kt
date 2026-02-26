package com.example.islandlyrics

import android.graphics.Bitmap
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
    oneuiCapsuleColorEnabled: Boolean = false
) {
    val context = LocalContext.current
    val repo = remember { LyricRepository.getInstance() }
    val metadata by repo.liveMetadata.observeAsState()
    val lyricInfo by repo.liveLyric.observeAsState()
    val albumArt by repo.liveAlbumArt.observeAsState()

    val title = metadata?.title ?: "Song Title"
    val artist = metadata?.artist ?: "Artist Name"
    val currentLyric = lyricInfo?.lyric ?: "Lyrics waiting..."
    
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
    
    // Layout Constants simulating the Capsule/Island
    val pillHeight = 56.dp // Standard Island height simulation
    val pillColor = if (oneuiCapsuleColorEnabled && extractedColor != null) extractedColor!! else Color.Black
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(pillHeight + 16.dp) // Container padding
            .background(Color.Transparent), // Let root background show
        contentAlignment = Alignment.Center
    ) {
        // The Capsule Itself
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillHeight)
                .clip(CircleShape) // Pill shape
                .background(pillColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // --- LEFT AREA (ICON) ---
            if (dynamicIconEnabled) {
                when (iconStyle) {
                    "advanced" -> {
                        // Advanced: [Art] [Title/Artist]
                        // Album Art
                        if (albumArt != null) {
                            Image(
                                bitmap = albumArt!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp) // 40dp fits safely in 56dp height
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
                        
                        // Text Info
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.widthIn(max = 120.dp) // Limit width so lyrics fit
                        ) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = artist,
                                color = Color.Gray,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    else -> {
                        // Classic: Text Only [Title - Artist]
                         Text(
                            text = "$title - $artist",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp)
                        )
                    }
                }
            } else {
                 // Disabled: App Icon (Music Note) - NO BACKGROUND
                 Icon(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp) // Standard icon size
                        // No background, no circle
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // --- RIGHT AREA (LYRICS) ---
            Text(
                text = currentLyric,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp, // Large lyric text
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun NotificationPreview(
    progressColorEnabled: Boolean,
    actionStyle: String,
    superIslandEnabled: Boolean = false,
    superIslandTextColorEnabled: Boolean = false
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
    
    val position = progressInfo?.position ?: 0L
    val duration = progressInfo?.duration ?: 100L
    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0.3f // Default 30% if no info

    // Calculate Color
    var extractedColor by remember { mutableStateOf<Color?>(null) }
    
    LaunchedEffect(albumArt) {
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
        } else {
            extractedColor = null
        }
    }
    
    val barColor = if (progressColorEnabled && extractedColor != null) extractedColor!! else MaterialTheme.colorScheme.primary
    val textColor = if (superIslandTextColorEnabled && extractedColor != null) extractedColor!! else Color.White
    val secondaryTextColor = if (superIslandTextColorEnabled && extractedColor != null) extractedColor!!.copy(alpha = 0.8f) else Color(0xFFB0B0B0)

    if (superIslandEnabled) {
        // Premium Super Island Style Notification Preview (As requested in image)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF081420)) // Deep navy/black
                .padding(16.dp)
        ) {
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
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.DarkGray)
                            )
                        }

                        // App Badge at bottom-right
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF333333))
                                .padding(2.dp)
                        ) {
                            Image(
                                painter = painterResource(R.mipmap.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Middle: Metadata
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (artist.isNotBlank()) "$artist - $currentLyric" else currentLyric,
                            color = secondaryTextColor,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Right: App Icon (Simulating the small icon on the right)
                    Icon(
                        painter = painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFF2D55)) // Pink/Red background for music
                            .padding(4.dp)
                    )
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
