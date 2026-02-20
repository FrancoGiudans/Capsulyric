package com.example.islandlyrics

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import androidx.palette.graphics.Palette

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaControlDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var activeControllers by remember { mutableStateOf<List<MediaController>>(emptyList()) }
    var statusMessage by remember { mutableStateOf(context.getString(R.string.media_control_scanning)) }
    
    // Check for HyperOS 3.0.300+
    val isHyperOsSupported = remember { RomUtils.isHyperOsVersionAtLeast(3, 0, 300) }

    // Load controllers & Listen for changes
    DisposableEffect(Unit) {
        val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = android.content.ComponentName(context, MediaMonitorService::class.java)

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            activeControllers = controllers ?: emptyList()
            statusMessage = context.getString(R.string.media_control_found_sessions, activeControllers.size)
        }

        try {
            // Initial load
            activeControllers = mediaSessionManager.getActiveSessions(componentName)
            statusMessage = context.getString(R.string.media_control_found_sessions, activeControllers.size)
            // Register listener
            mediaSessionManager.addOnActiveSessionsChangedListener(listener, componentName)
        } catch (e: SecurityException) {
            statusMessage = context.getString(R.string.media_control_perm_required)
        } catch (e: Exception) {
            statusMessage = context.getString(R.string.media_control_error_prefix, e.message)
        }

        onDispose {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(listener)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Filter by Whitelist
    val whitelistedControllers = remember(activeControllers) {
        val enabledPackages = ParserRuleHelper.getEnabledPackages(context)
        activeControllers.filter { enabledPackages.contains(it.packageName) }
    }
    
    // Repo for lyrics
    val repo = remember { LyricRepository.getInstance() }
    val repoMetadata by repo.liveMetadata.observeAsState()

    val repoLyric by repo.liveLyric.observeAsState()
    val repoProgress by repo.liveProgress.observeAsState()

    // Transition State for Enter/Exit animations
    val visibleState = remember { MutableTransitionState(true) }

    // Handle dismissal with animation
    val triggerDismiss = {
        visibleState.targetState = false
    }

    // Actual dismiss when animation finishes (i.e. both states are false)
    if (!visibleState.targetState && !visibleState.currentState) {
        LaunchedEffect(Unit) {
            onDismiss()
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = triggerDismiss) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = EnterTransition.None,
            exit = scaleOut() + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp), 
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp) 
                    .wrapContentHeight() 
            ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.media_control_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    // Open App Button (Pill Icon)
                    IconButton(onClick = {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(intent)
                        triggerDismiss()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pill),
                            contentDescription = "Open Capsulyric",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Mi Play Action (Top Right)
                    if (isHyperOsSupported) {
                        IconButton(onClick = {
                            try {
                                val intent = Intent()
                                intent.component = android.content.ComponentName(
                                    "miui.systemui.plugin",
                                    "miui.systemui.miplay.MiPlayDetailActivity"
                                )
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                                    context.startActivity(intent)
                                    triggerDismiss() 
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.media_control_miplay_failed, e.message), Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_miplay),
                                    contentDescription = "Mi Play",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    
                        // Small info icon for status
                        IconButton(onClick = { 
                             Toast.makeText(context, "$statusMessage (Whitelisted: ${whitelistedControllers.size})", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_info),
                                contentDescription = "Status",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                if (whitelistedControllers.isNotEmpty()) {
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { whitelistedControllers.size })
                    
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 0.dp), // No extra padding needed
                        pageSpacing = 16.dp // Separation between cards
                    ) { page ->
                        key(whitelistedControllers[page].packageName) {
                            val controller = whitelistedControllers[page]
                            val isPrimary = controller.packageName == repoMetadata?.packageName
                            
                            MediaSessionCard(
                                controller = controller, 
                                context = context,
                                isPrimary = isPrimary,
                                primaryLyric = if (isPrimary) repoLyric?.lyric else null,
                                primaryProgress = if (isPrimary) repoProgress else null
                            )
                        }
                    }
                    
                    // Simple Pager Indicator
                    if (whitelistedControllers.size > 1) {
                         Row(
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(whitelistedControllers.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(6.dp)
                                        .background(color, CircleShape)
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.media_control_no_sessions), color = MaterialTheme.colorScheme.error)
                    }
                }
                
                // Close Button - Full Width Outlined Button
                OutlinedButton(
                    onClick = triggerDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.media_control_close))
                }
            }
        }
    }
}
}

@Composable
fun MediaSessionCard(
    controller: MediaController, 
    context: Context,

    isPrimary: Boolean,
    primaryLyric: String?,
    primaryProgress: LyricRepository.PlaybackProgress?
) {
    // Observable state for the controller
    var playbackState by remember(controller) { mutableStateOf(controller.playbackState) }
    var metadata by remember(controller) { mutableStateOf(controller.metadata) }

    DisposableEffect(controller) {
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                playbackState = state
            }
            override fun onMetadataChanged(meta: MediaMetadata?) {
                metadata = meta
            }
        }
        controller.registerCallback(callback)
        onDispose {
            controller.unregisterCallback(callback)
        }
    }

    val pkg = controller.packageName
    
    // --- PARSING LOGIC ---
    var title by remember { mutableStateOf(context.getString(R.string.media_control_unknown_title)) }
    var artist by remember { mutableStateOf(context.getString(R.string.media_control_unknown_artist)) }
    var parsedLyricFromTitle by remember { mutableStateOf<String?>(null) }
    
    val rawTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val rawArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)

    LaunchedEffect(rawTitle, rawArtist, pkg) {
        var finalTitle = rawTitle ?: context.getString(R.string.media_control_unknown_title)
        var finalArtist = rawArtist ?: context.getString(R.string.media_control_unknown_artist)
        parsedLyricFromTitle = null
        
        val rule = ParserRuleHelper.getRuleForPackage(context, pkg)
        if (rule != null && rule.enabled) {
             // Case 1: Title parsing
             val titleParse = ParserRuleHelper.parseWithRule(rawTitle ?: "", rule)
             if (titleParse.third) {
                 finalTitle = titleParse.first
                 finalArtist = titleParse.second
             } else {
                 // Case 2: Artist parsing + Title is Lyric
                 val artistParse = ParserRuleHelper.parseWithRule(rawArtist ?: "", rule)
                 if (artistParse.third) {
                     finalTitle = artistParse.first
                     finalArtist = artistParse.second
                     if (!rawTitle.isNullOrEmpty()) {
                         parsedLyricFromTitle = rawTitle
                     }
                 }
             }
        }
        title = finalTitle
        artist = finalArtist
    }
    // ---------------------

    val albumArtBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
    val appName = remember(pkg) { ParserRuleHelper.getAppNameForPackage(context, pkg) }
    val appIcon = remember(pkg) {
        try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(pkg)
            val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: run {
                val bmp = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING || playbackState?.state == PlaybackState.STATE_BUFFERING
    val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val position = if (isPrimary) primaryProgress?.position ?: 0L else playbackState?.position ?: 0L

    // Dynamic Color - Same as before ...
    var cardBackgroundColor by remember { mutableStateOf(Color.Unspecified) }
    val defaultSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    LaunchedEffect(albumArtBitmap) {
        if (albumArtBitmap != null) {
            Palette.from(albumArtBitmap).generate { palette ->
                val vibrant = palette?.vibrantSwatch?.rgb
                val dominant = palette?.dominantSwatch?.rgb
                val colorInt = vibrant ?: dominant
                if (colorInt != null) {
                    // Apply alpha to blend with surface, or use as is
                    // Let's use it as a tint mixed with surface for better readability, or just the color if it's light enough?
                    // Usually we want a container color.
                    cardBackgroundColor = Color(colorInt).copy(alpha = 0.3f) // Add transparency
                }
            }
        } else {
            cardBackgroundColor = Color.Unspecified
        }
    }
    
    // Fuse extracted color with surface variant default
    val finalContainerColor = if (cardBackgroundColor != Color.Unspecified) {
        // Composite over surface to make it opaque-ish
        // Actually, just using the color with alpha over SurfaceVariant is fine for a tint effect
         MaterialTheme.colorScheme.surfaceVariant // fallback base
         // We will apply the tint via a Box or modify CardDefaults?
         // Let's just use the color directly if available, but ensure it's not too dark/light compared to text
         // A safe bet is mixing it with Surface.
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
             containerColor = if (cardBackgroundColor != Color.Unspecified) 
                MaterialTheme.colorScheme.surface.copy(alpha = 1f) // Reset to surface then overlay
             else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Overlay for tint if we have a dynamic color
        // Overlay for tint if we have a dynamic color
        Box(modifier = Modifier.fillMaxWidth().background(
            if (cardBackgroundColor != Color.Unspecified) cardBackgroundColor else Color.Transparent
        )) {
            Column(modifier = Modifier.padding(20.dp)) {
                
                // Top Row: Album Art + Info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Album Art
                    if (albumArtBitmap != null) {
                        Image(
                            bitmap = albumArtBitmap.asImageBitmap(),
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceDim,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_music_note),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (appIcon != null) {
                                    Image(
                                        bitmap = appIcon.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = appName, 
                                    style = MaterialTheme.typography.labelMedium, 
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // Package Name on next line
                            Text(
                                text = pkg, 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(start = if (appIcon != null) 22.dp else 0.dp) // Indent to align with text
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                // Lyrics (primary or parsed from title)
                val effectiveLyric = if (isPrimary && !primaryLyric.isNullOrBlank()) primaryLyric else parsedLyricFromTitle

                // Use fixed height to match Homepage card style
                Column(modifier = Modifier.height(90.dp)) {
                    if (!effectiveLyric.isNullOrBlank()) {
                        Text(
                            text = "Lyric:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = effectiveLyric,
                            style = MaterialTheme.typography.bodyLarge,
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface, 
                            lineHeight = 24.sp
                        )
                    } else {
                         Text(
                            text = "Lyric:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.media_control_no_sessions), // placeholder text or empty
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                
                // Progress Bar (Interactive Slider)
                var isDragging by remember { mutableStateOf(false) }
                var dragProgress by remember { mutableFloatStateOf(0f) }

                val currentProgress = if (isDragging) {
                    dragProgress
                } else {
                    if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                }

                Slider(
                    value = currentProgress,
                    onValueChange = { 
                        isDragging = true
                        dragProgress = it
                    },
                    onValueChangeFinished = {
                        if (duration > 0) {
                             controller.transportControls.seekTo((dragProgress * duration).toLong())
                        }
                        isDragging = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp) 
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { controller.transportControls.skipToPrevious() }) {
                        Icon(painterResource(R.drawable.ic_skip_previous), "Prev", modifier = Modifier.size(32.dp))
                    }
                    
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                controller.transportControls.pause()
                            } else {
                                controller.transportControls.play()
                            }
                        },
                        modifier = Modifier
                            .size(56.dp) // Larger play button
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                         Icon(
                             painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow), 
                             "Toggle",
                             tint = MaterialTheme.colorScheme.onPrimaryContainer,
                             modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    IconButton(onClick = { controller.transportControls.skipToNext() }) {
                        Icon(painterResource(R.drawable.ic_skip_next), "Next", modifier = Modifier.size(28.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Actions
                Button(
                    onClick = {
                        try {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            } else {
                                Toast.makeText(context, context.getString(R.string.media_control_cannot_open, pkg), Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.media_control_error_prefix, e.message), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.media_control_open_app, appName))
                }
            }
        }
    }
}
