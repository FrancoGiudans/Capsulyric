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

package com.example.islandlyrics.feature.mediacontrol.material

import android.content.Context
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.runtime.service.MediaMonitorService
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.lyrics.cache.OnlineLyricCacheStore
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.feature.main.HomeLyricPreviewDisplay
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.feature.onlinelyricdebug.OnlineLyricDebugActivity
import com.example.islandlyrics.ui.material.blur.LocalMaterialBlurEnabled
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.livedata.observeAsState
import androidx.palette.graphics.Palette
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val repoParsedLyrics by repo.liveParsedLyrics.observeAsState()
    val repoCurrentLine by repo.liveCurrentLine.observeAsState()
    val repoLyricResolveState by repo.liveLyricResolveState.observeAsState(LyricRepository.LyricResolveState.PENDING)
    val homeLyricPreviewModes = remember { HomeLyricPreviewDisplay.read(AppPreferences.of(context)) }
    val repoNextLine = remember(repoParsedLyrics, repoCurrentLine) {
        val lines = repoParsedLyrics?.lines
        if (lines.isNullOrEmpty() || repoCurrentLine == null) {
            null
        } else {
            val index = lines.indexOfFirst {
                it === repoCurrentLine || it.startTime == repoCurrentLine?.startTime
            }
            if (index >= 0 && index + 1 < lines.size) lines[index + 1] else null
        }
    }
    val homeLyricPreview = HomeLyricPreviewDisplay.previewText(
        modes = homeLyricPreviewModes,
        currentLine = repoCurrentLine,
        lyricInfo = repoLyric,
        nextLine = repoNextLine
    )
    val onlineLyricCacheStore = remember(context) { OnlineLyricCacheStore(context) }
    var isCurrentTrackInstrumental by remember { mutableStateOf(false) }
    LaunchedEffect(
        repoMetadata?.packageName,
        repoMetadata?.title,
        repoMetadata?.artist,
        repoMetadata?.rawTitle,
        repoMetadata?.rawArtist,
        repoLyric?.apiPath,
        repoLyric?.lyric
    ) {
        val mediaInfo = repoMetadata
        isCurrentTrackInstrumental = if (mediaInfo == null) {
            false
        } else {
            withContext(Dispatchers.IO) {
                val rule = ParserRuleHelper.getRuleForPackage(context, mediaInfo.packageName)
                    ?: ParserRuleHelper.createDefaultRule(mediaInfo.packageName)
                onlineLyricCacheStore.getCurrentSongState(
                    mediaInfo = mediaInfo,
                    fallbackTitle = mediaInfo.title,
                    fallbackArtist = mediaInfo.artist,
                    useRawMetadata = rule.useRawMetadataForOnlineMatching
                ).isInstrumental
            }
        }
    }
    val activeParsedLyrics = repoParsedLyrics?.takeIf { it.lines.isNotEmpty() }
    val primaryLyricSource = formatPrimaryLyricSource(
        apiPath = activeParsedLyrics?.apiPath ?: repoLyric?.apiPath,
        sourceLabel = activeParsedLyrics?.sourceLabel,
        sourceApp = repoLyric?.sourceApp
    )
    val showOnlineLyricRematch = !OfflineModeManager.isEnabled(context) &&
        (isOnlineLyricSource(repoLyric?.apiPath) ||
            isOnlineLyricSource(repoParsedLyrics?.apiPath) ||
            (repoLyric?.lyric.isNullOrBlank() && repoParsedLyrics?.lines.isNullOrEmpty()))

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

    val blurEnabled = LocalMaterialBlurEnabled.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = triggerDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogShape = RoundedCornerShape(24.dp)
        val view = LocalView.current
        val density = LocalDensity.current

        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                val cornerRadiusPx = with(density) { 24.dp.toPx() }
                val screenWidthPx = view.resources.displayMetrics.widthPixels
                val maxWidthPx = with(density) { 420.dp.roundToPx() }
                val horizontalMarginPx = with(density) { 16.dp.roundToPx() }
                val targetWidthPx = minOf(screenWidthPx - horizontalMarginPx * 2, maxWidthPx).coerceAtLeast(0)
                window.attributes = window.attributes.apply {
                    width = targetWidthPx
                    height = android.view.WindowManager.LayoutParams.WRAP_CONTENT
                }
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0f)
                window.setBackgroundDrawable(
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = cornerRadiusPx
                        setColor(android.graphics.Color.TRANSPARENT)
                    }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        window.setBackgroundBlurRadius(
                            if (blurEnabled && visibleState.currentState) 140 else 0
                        )
                    } catch (_: Exception) {}
                }
            }
        }

        AnimatedVisibility(
            visibleState = visibleState,
            enter = EnterTransition.None,
            exit = scaleOut() + fadeOut()
        ) {
            Surface(
                shape = dialogShape,
                color = if (blurEnabled) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.38f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
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
                            contentPadding = PaddingValues(horizontal = 0.dp),
                            pageSpacing = 16.dp
                        ) { page ->
                            key(whitelistedControllers[page].packageName) {
                                val controller = whitelistedControllers[page]
                                val isPrimary = controller.packageName == repoMetadata?.packageName

                                MediaSessionCard(
                                    controller = controller,
                                    context = context,
                                    isPrimary = isPrimary,
                                    primaryLyric = if (isPrimary) homeLyricPreview else null,
                                    primaryHasTimeline = isPrimary && repoParsedLyrics?.lines?.isNotEmpty() == true,
                                    primaryLyricResolveState = repoLyricResolveState,
                                    primaryProgress = if (isPrimary) repoProgress else null,
                                    primaryLyricSource = if (isPrimary) primaryLyricSource else null,
                                    primaryIsInstrumental = isPrimary && isCurrentTrackInstrumental,
                                    showOnlineLyricRematch = isPrimary && showOnlineLyricRematch,
                                    onOpenOnlineLyricRematch = {
                                        context.startActivity(Intent(context, OnlineLyricDebugActivity::class.java))
                                        triggerDismiss()
                                    }
                                )
                            }
                        }

                        if (whitelistedControllers.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                repeat(whitelistedControllers.size) { iteration ->
                                    val color = if (pagerState.currentPage == iteration) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.media_control_no_sessions),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

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
    primaryHasTimeline: Boolean,
    primaryLyricResolveState: LyricRepository.LyricResolveState,
    primaryProgress: LyricRepository.PlaybackProgress?,
    primaryLyricSource: String?,
    primaryIsInstrumental: Boolean,
    showOnlineLyricRematch: Boolean,
    onOpenOnlineLyricRematch: () -> Unit
) {
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
        } catch (_: Exception) {
            null
        }
    }
    val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING || playbackState?.state == PlaybackState.STATE_BUFFERING
    val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val position = if (isPrimary) primaryProgress?.position ?: 0L else playbackState?.position ?: 0L
    val sessionStateKey = buildString {
        append(pkg)
        append('|')
        append(title)
        append('|')
        append(artist)
    }
    var pendingSeekPosition by remember(sessionStateKey) { mutableLongStateOf(-1L) }
    var pendingSeekStartedAt by remember(sessionStateKey) { mutableLongStateOf(0L) }
    var seekOriginPosition by remember(sessionStateKey) { mutableLongStateOf(-1L) }
    var cardBackgroundColor by remember { mutableStateOf(Color.Transparent) }

    LaunchedEffect(albumArtBitmap) {
        if (albumArtBitmap == null) {
            cardBackgroundColor = Color.Transparent
        } else {
            Palette.from(albumArtBitmap).generate { palette ->
                val colorInt = palette?.vibrantSwatch?.rgb ?: palette?.dominantSwatch?.rgb
                cardBackgroundColor = colorInt?.let { Color(it).copy(alpha = 0.22f) } ?: Color.Transparent
            }
        }
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(cardBackgroundColor)) {
            Column(modifier = Modifier.padding(20.dp)) {
                val openApp = {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.media_control_cannot_open, pkg),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.media_control_error_prefix, e.message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = openApp)
                    ) {
                        if (albumArtBitmap != null) {
                            Image(
                                bitmap = albumArtBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.main_album_art_cd),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceDim,
                                modifier = Modifier.matchParentSize()
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }

                        if (appIcon != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                            ) {
                                Image(
                                    bitmap = appIcon.asImageBitmap(),
                                    contentDescription = appName,
                                    modifier = Modifier.padding(4.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = appName,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val primaryStatus = when {
                            isPrimary && primaryIsInstrumental -> stringResource(R.string.main_instrumental_marked)
                            isPrimary && !primaryLyricSource.isNullOrBlank() ->
                                stringResource(R.string.main_lyric_source_fmt, primaryLyricSource)
                            else -> null
                        }
                        if (primaryStatus != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = primaryStatus,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                val effectiveLyric = if (isPrimary && !primaryLyric.isNullOrBlank()) primaryLyric else parsedLyricFromTitle
                MaterialLyricPanel(
                    lyric = effectiveLyric,
                    emptyText = if (isPrimary) {
                        when {
                            primaryIsInstrumental -> stringResource(R.string.main_no_lyrics)
                            primaryHasTimeline -> stringResource(
                                R.string.main_lyrics_sync_placeholder_fmt,
                                formatPlaybackTime(position),
                                formatPlaybackTime(duration)
                            )
                            primaryLyricResolveState == LyricRepository.LyricResolveState.NOT_FOUND ->
                                stringResource(R.string.main_lyrics_not_found)
                            else -> stringResource(R.string.main_waiting_for_lyrics)
                        }
                    } else {
                        stringResource(R.string.main_lyrics_unavailable)
                    }
                )
                if (showOnlineLyricRematch) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenOnlineLyricRematch,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.online_lyric_rematch_entry_short))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                var isDragging by remember { mutableStateOf(false) }
                var dragProgress by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(position, pendingSeekPosition, pendingSeekStartedAt, seekOriginPosition, isPlaying) {
                    if (pendingSeekPosition < 0L) return@LaunchedEffect
                    val expectedPendingPosition = if (isPlaying && pendingSeekStartedAt > 0L) {
                        (pendingSeekPosition + (SystemClock.elapsedRealtime() - pendingSeekStartedAt)).coerceAtMost(duration)
                    } else {
                        pendingSeekPosition
                    }
                    val hasActuallyMovedFromOrigin = seekOriginPosition < 0L || abs(position - seekOriginPosition) > 250L
                    val isNearSeekTarget = abs(position - expectedPendingPosition) <= 250L

                    if (hasActuallyMovedFromOrigin && isNearSeekTarget) {
                        pendingSeekPosition = -1L
                        pendingSeekStartedAt = 0L
                        seekOriginPosition = -1L
                        return@LaunchedEffect
                    }
                    if (pendingSeekStartedAt <= 0L) return@LaunchedEffect
                    val remaining = 4000L - (SystemClock.elapsedRealtime() - pendingSeekStartedAt)
                    if (remaining > 0L) delay(remaining)
                    if (pendingSeekPosition >= 0L && !isPlaying) {
                        pendingSeekPosition = -1L
                        pendingSeekStartedAt = 0L
                        seekOriginPosition = -1L
                    }
                }
                val effectivePosition = if (pendingSeekPosition >= 0L) {
                    if (isPlaying && pendingSeekStartedAt > 0L) {
                        (pendingSeekPosition + (SystemClock.elapsedRealtime() - pendingSeekStartedAt)).coerceAtMost(duration)
                    } else {
                        pendingSeekPosition
                    }
                } else {
                    position
                }
                val currentProgress = if (isDragging) {
                    dragProgress
                } else {
                    if (duration > 0) (effectivePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                }

                MaterialMediaProgressBar(
                    value = currentProgress,
                    enabled = duration > 0,
                    isPlaying = isPlaying,
                    isDragging = isDragging,
                    onValueChange = {
                        isDragging = true
                        dragProgress = it
                    },
                    onValueChangeFinished = {
                        if (duration > 0) {
                            seekOriginPosition = position
                            pendingSeekPosition = (dragProgress * duration).toLong()
                            pendingSeekStartedAt = SystemClock.elapsedRealtime()
                            controller.transportControls.seekTo(pendingSeekPosition)
                        }
                        isDragging = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialRoundTransportButton(R.drawable.ic_skip_previous, stringResource(R.string.action_previous)) {
                        controller.transportControls.skipToPrevious()
                    }
                    FilledTonalButton(
                        onClick = {
                            if (isPlaying) {
                                controller.transportControls.pause()
                            } else {
                                controller.transportControls.play()
                            }
                        },
                        shape = CircleShape,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                            contentDescription = if (isPlaying) {
                                stringResource(R.string.action_pause)
                            } else {
                                stringResource(R.string.action_play)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    MaterialRoundTransportButton(R.drawable.ic_skip_next, stringResource(R.string.action_next)) {
                        controller.transportControls.skipToNext()
                    }
                }
            }
        }
    }
}

private fun isOnlineLyricSource(apiPath: String?): Boolean {
    return apiPath == "Online API" || apiPath == "Online Cache"
}

private fun formatPrimaryLyricSource(
    apiPath: String?,
    sourceLabel: String?,
    sourceApp: String?
): String? {
    val label = sourceLabel?.trim().takeUnless { it.isNullOrBlank() }
        ?: sourceApp?.trim().takeUnless { it.isNullOrBlank() }
    return when (apiPath) {
        "Online API" -> label?.let { "$it [在线]" } ?: "在线歌词"
        "Online Cache" -> label?.let { "$it [缓存]" } ?: "在线歌词缓存"
        "Local LRC" -> label ?: "本地歌词"
        "Lyricon" -> label?.let { "$it [Lyricon]" } ?: "Lyricon"
        "SuperLyric" -> label?.let { "$it [SuperLyric]" } ?: "SuperLyric"
        "Lyric Getter" -> label?.let { "$it [Lyric Getter]" } ?: "Lyric Getter"
        "Notification" -> label?.let { "$it [通知歌词]" } ?: "通知歌词"
        else -> label ?: apiPath?.trim().takeUnless { it.isNullOrBlank() }
    }
}

private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun MaterialLyricPanel(
    lyric: String?,
    emptyText: String,
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .clickable(enabled = !lyric.isNullOrBlank()) {
                lyric?.let {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Lyric", it))
                    Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                }
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF3D7DFF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.main_current_lyric_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (!lyric.isNullOrBlank()) {
                Text(
                    text = lyric,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    color = Color(0xFF3D7DFF)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.tap_to_copy_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MaterialRoundTransportButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, shape = CircleShape, contentPadding = PaddingValues(14.dp)) {
        Icon(painter = painterResource(iconRes), contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MaterialMediaProgressBar(
    value: Float,
    enabled: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
    val activeTrackColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.primary
    val showWavyIndicator = enabled && isPlaying && !isDragging

    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    onValueChange((offset.x / width).coerceIn(0f, 1f))
                    onValueChangeFinished()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        onValueChange((offset.x / width).coerceIn(0f, 1f))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        onValueChange((change.position.x / width).coerceIn(0f, 1f))
                    },
                    onDragEnd = onValueChangeFinished,
                    onDragCancel = onValueChangeFinished
                )
            }
    ) {
        val clamped = value.coerceIn(0f, 1f)
        val width = maxWidth
        val thumbSize = if (isDragging && enabled) 12.dp else 0.dp
        androidx.compose.runtime.key(showWavyIndicator) {
            AndroidView(
                factory = { context ->
                    val themedContext = ContextThemeWrapper(
                        context,
                        if (showWavyIndicator) {
                            R.style.ThemeOverlay_IslandLyrics_MediaProgress_Wavy
                        } else {
                            R.style.ThemeOverlay_IslandLyrics_MediaProgress_Flat
                        }
                    )
                    LinearProgressIndicator(themedContext, null).apply {
                        max = 10_000
                        isIndeterminate = false
                        setTrackStopIndicatorSize(0)
                        setIndicatorColor(activeTrackColor.toArgb())
                        trackColor = inactiveTrackColor.toArgb()
                        setProgressCompat((clamped * max).roundToInt(), false)
                    }
                },
                update = { indicator ->
                    indicator.setIndicatorColor(activeTrackColor.toArgb())
                    indicator.trackColor = inactiveTrackColor.toArgb()
                    indicator.alpha = if (enabled) 1f else 0.38f
                    indicator.setProgressCompat((clamped * indicator.max).roundToInt(), false)
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(28.dp)
            )
        }

        if (thumbSize > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = (width * clamped - thumbSize / 2).coerceAtLeast(0.dp))
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }
    }
}
