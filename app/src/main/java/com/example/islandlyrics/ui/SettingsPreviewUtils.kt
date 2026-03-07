package com.example.islandlyrics.ui

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
fun NotificationPreview(
    progressColorEnabled: Boolean,
    actionStyle: String,
    superIslandEnabled: Boolean = true
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
        }
    }
    
    val barColor = if (progressColorEnabled && extractedColor != null) extractedColor!! else MaterialTheme.colorScheme.primary

    // Hardcoded to Super Island Style for Android 15 Special Version
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF0C0C0C)) // Xiaomi Island Dark
            .padding(16.dp)
    ) {
        // Template 12 Layout: [Album Art] [Lyrics / Title-Artist] [Buttons Group]
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
                    text = currentLyric,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (artist.isNotBlank()) "$title - $artist" else title,
                    color = Color(0xFFB0B0B0),
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
                // Play/Pause with Progress Ring
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
                        color = if (progressColorEnabled) barColor else Color.White,
                        trackColor = Color.Transparent,
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
    }
}
