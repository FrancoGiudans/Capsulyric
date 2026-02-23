package com.example.islandlyrics

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
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
import androidx.palette.graphics.Palette
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MiuixMediaControlDialog(
    show: MutableState<Boolean>,
    onDismiss: () -> Unit
) {
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
            activeControllers = mediaSessionManager.getActiveSessions(componentName)
            statusMessage = context.getString(R.string.media_control_found_sessions, activeControllers.size)
            mediaSessionManager.addOnActiveSessionsChangedListener(listener, componentName)
        } catch (e: SecurityException) {
            statusMessage = context.getString(R.string.media_control_perm_required)
        } catch (e: Exception) {
            statusMessage = context.getString(R.string.media_control_error_prefix, e.message)
        }

        onDispose {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(listener)
            } catch (e: Exception) {}
        }
    }

    val whitelistedControllers = remember(activeControllers) {
        val enabledPackages = ParserRuleHelper.getEnabledPackages(context)
        activeControllers.filter { enabledPackages.contains(it.packageName) }
    }
    
    val repo = remember { LyricRepository.getInstance() }
    val repoMetadata by repo.liveMetadata.observeAsState()
    val repoLyric by repo.liveLyric.observeAsState()
    val repoProgress by repo.liveProgress.observeAsState()

    SuperDialog(
        title = stringResource(R.string.media_control_title),
        show = show,
        onDismissRequest = {
            show.value = false
            onDismiss()
        }
    ) {
        Column(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Actions Row (Mi Play, Open App, Info)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(intent)
                    show.value = false
                    onDismiss()
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pill),
                        contentDescription = "Open Capsulyric",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }

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
                            show.value = false
                            onDismiss()
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.media_control_miplay_failed, e.message), Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_miplay),
                            contentDescription = "Mi Play",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }

                IconButton(onClick = { 
                    Toast.makeText(context, "$statusMessage (Whitelisted: ${whitelistedControllers.size})", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = "Status",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (whitelistedControllers.isNotEmpty()) {
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { whitelistedControllers.size })
                
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    pageSpacing = 16.dp
                ) { page ->
                    key(whitelistedControllers[page].packageName) {
                        val controller = whitelistedControllers[page]
                        val isPrimary = controller.packageName == repoMetadata?.packageName
                        
                        MiuixMediaSessionCard(
                            controller = controller, 
                            context = context,
                            isPrimary = isPrimary,
                            primaryLyric = if (isPrimary) repoLyric?.lyric else null,
                            primaryProgress = if (isPrimary) repoProgress else null
                        )
                    }
                }
                
                if (whitelistedControllers.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(whitelistedControllers.size) { iteration ->
                            val color = if (pagerState.currentPage == iteration) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondary
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
                    Text(stringResource(R.string.media_control_no_sessions), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
}

@Composable
fun MiuixMediaSessionCard(
    controller: MediaController, 
    context: Context,
    isPrimary: Boolean,
    primaryLyric: String?,
    primaryProgress: LyricRepository.PlaybackProgress?
) {
    var playbackState by remember(controller) { mutableStateOf(controller.playbackState) }
    var metadata by remember(controller) { mutableStateOf(controller.metadata) }

    DisposableEffect(controller) {
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) { playbackState = state }
            override fun onMetadataChanged(meta: MediaMetadata?) { metadata = meta }
        }
        controller.registerCallback(callback)
        onDispose { controller.unregisterCallback(callback) }
    }

    val pkg = controller.packageName
    
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
             val titleParse = ParserRuleHelper.parseWithRule(rawTitle ?: "", rule)
             if (titleParse.third) {
                 finalTitle = titleParse.first
                 finalArtist = titleParse.second
             } else {
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

    val albumArtBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
    val appName = remember(pkg) { ParserRuleHelper.getAppNameForPackage(context, pkg) }
    val appIcon = remember(pkg) {
        try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(pkg)
            (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: run {
                val bmp = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        } catch (e: Exception) { null }
    }

    val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING || playbackState?.state == PlaybackState.STATE_BUFFERING
    val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val position = if (isPrimary) primaryProgress?.position ?: 0L else playbackState?.position ?: 0L

    var cardBackgroundColor by remember { mutableStateOf(Color.Unspecified) }

    LaunchedEffect(albumArtBitmap) {
        if (albumArtBitmap != null) {
            Palette.from(albumArtBitmap).generate { palette ->
                val vibrant = palette?.vibrantSwatch?.rgb
                val dominant = palette?.dominantSwatch?.rgb
                val colorInt = vibrant ?: dominant
                if (colorInt != null) {
                    cardBackgroundColor = Color(colorInt).copy(alpha = 0.3f)
                }
            }
        } else {
            cardBackgroundColor = Color.Unspecified
        }
    }
    
    val fallbackBackgroundColor = MiuixTheme.colorScheme.secondary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (cardBackgroundColor != Color.Unspecified) cardBackgroundColor else fallbackBackgroundColor
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            
            // Top Row: Album Art + Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (albumArtBitmap != null) {
                    Image(
                        bitmap = albumArtBitmap.asImageBitmap(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MiuixTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_music_note),
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = artist, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = appName, 
                            fontSize = 12.sp, 
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            // Lyrics
            val effectiveLyric = if (isPrimary && !primaryLyric.isNullOrBlank()) primaryLyric else parsedLyricFromTitle

            Column(modifier = Modifier.height(70.dp)) {
                if (!effectiveLyric.isNullOrBlank()) {
                    Text(
                        text = "Lyric:",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = effectiveLyric,
                        fontSize = 16.sp,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurface, 
                        lineHeight = 22.sp
                    )
                } else {
                    Text(
                        text = "Lyric:",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.media_control_no_sessions),
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            var dragProgress by remember { mutableFloatStateOf(0f) }
            val currentProgress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

            Slider(
                value = dragProgress,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { controller.transportControls.skipToPrevious() }) {
                    Icon(painterResource(R.drawable.ic_skip_previous), "Prev", tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
                }
                
                IconButton(
                    onClick = {
                        if (isPlaying) controller.transportControls.pause() else controller.transportControls.play()
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .background(MiuixTheme.colorScheme.primary, CircleShape)
                ) {
                     Icon(
                         painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow), 
                         "Toggle",
                         tint = MiuixTheme.colorScheme.onPrimary,
                         modifier = Modifier.size(36.dp)
                    )
                }
                
                IconButton(onClick = { controller.transportControls.skipToNext() }) {
                    Icon(painterResource(R.drawable.ic_skip_next), "Next", tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}
