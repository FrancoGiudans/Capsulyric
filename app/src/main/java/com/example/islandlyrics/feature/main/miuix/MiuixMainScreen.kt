package com.example.islandlyrics.feature.main.miuix

import android.graphics.Bitmap
import com.example.islandlyrics.R
import com.example.islandlyrics.service.MediaMonitorService
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.LyricRepository
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.palette.graphics.Palette
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference as SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.feature.update.miuix.MiuixUpdateDialog
import com.example.islandlyrics.ui.miuix.*

private val StatusActive = Color(0xFF4CAF50)
private val StatusInactive = Color(0xFFF44336)

@Composable
fun MiuixMainScreen(
    versionText: String,
    isDebugBuild: Boolean,
    onOpenSettings: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenPromotedSettings: () -> Unit,
    onStatusCardTap: () -> Unit,
    updateReleaseInfo: UpdateChecker.ReleaseInfo? = null,
    onUpdateDismiss: () -> Unit = {},
    onUpdateIgnore: (String) -> Unit = {}
) {
    val repo = remember { LyricRepository.getInstance() }
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    val repoPlaying by repo.isPlaying.observeAsState(false)
    val repoMetadata by repo.liveMetadata.observeAsState()
    val repoLyric by repo.liveLyric.observeAsState()
    val repoAlbumArt by repo.liveAlbumArt.observeAsState()
    val repoProgress by repo.liveProgress.observeAsState()
    val repoParsedLyrics by repo.liveParsedLyrics.observeAsState()

    var activeControllers by remember { mutableStateOf<List<MediaController>>(emptyList()) }

    DisposableEffect(Unit) {
        val mediaSessionManager = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = android.content.ComponentName(context, MediaMonitorService::class.java)
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            activeControllers = controllers ?: emptyList()
        }
        try {
            activeControllers = mediaSessionManager.getActiveSessions(componentName)
            mediaSessionManager.addOnActiveSessionsChangedListener(listener, componentName)
        } catch (_: Exception) {}
        onDispose {
            try { mediaSessionManager.removeOnActiveSessionsChangedListener(listener) } catch (_: Exception) {}
        }
    }

    val whitelistedSessions = remember(activeControllers) {
        val enabledPackages = ParserRuleHelper.getEnabledPackages(context)
        activeControllers
            .filter { enabledPackages.contains(it.packageName) }
            .sortedByDescending {
                val state = it.playbackState?.state
                state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
            }
    }

    val listenerEnabled = remember(Unit) {
        val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        listeners?.contains(context.packageName) == true
    }
    val serviceConnected = MediaMonitorService.isConnected
    val hasActiveSession = whitelistedSessions.isNotEmpty()

    val statusText = when {
        !listenerEnabled -> "Permission Required"
        hasActiveSession -> {
            if (repoPlaying || repoMetadata != null) {
                val rawPackage = repoLyric?.sourceApp ?: repoMetadata?.packageName ?: whitelistedSessions.firstOrNull()?.packageName
                val sourceName = if (rawPackage != null) ParserRuleHelper.getAppNameForPackage(context, rawPackage) else "Music"
                "Active: $sourceName"
            } else {
                "Ready: ${whitelistedSessions.size} Session(s)"
            }
        }
        !serviceConnected -> "Service Disconnected\nTap to Reconnect"
        else -> "Service Ready (Idle)"
    }
    val statusActive = listenerEnabled && serviceConnected

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurSmallTopAppBar(
                title = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    val appIconBitmap = remember {
                        val drawable = context.packageManager.getApplicationIcon(context.packageName)
                        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, 128, 128)
                        drawable.draw(canvas)
                        bmp
                    }
                    Image(
                        bitmap = appIconBitmap.asImageBitmap(),
                        contentDescription = "App Icon",
                        modifier = Modifier.padding(start = 16.dp).size(32.dp).clip(CircleShape)
                    )
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            item {
                MiuixStatusPill(
                    statusText = statusText,
                    isActive = statusActive,
                    onTap = if (!serviceConnected && listenerEnabled) onStatusCardTap else null
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                if (whitelistedSessions.isEmpty()) {
                    MiuixIdleCard()
                } else {
                    Column {
                        val pagerState = rememberPagerState(pageCount = { whitelistedSessions.size })

                        HorizontalPager(
                            state = pagerState,
                            pageSpacing = 16.dp,
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { page ->
                            val controller = whitelistedSessions[page]
                            val isPrimary = controller.packageName == repoMetadata?.packageName
                            MiuixMediaSessionCard(
                                controller = controller,
                                isPrimary = isPrimary,
                                primaryMetadata = if (isPrimary) repoMetadata else null,
                                primaryLyric = if (isPrimary) repoLyric?.lyric else null,
                                primaryLyricSource = if (isPrimary && repoLyric?.apiPath == "Online API") repoParsedLyrics?.sourceLabel else null,
                                primaryAlbumArt = if (isPrimary) repoAlbumArt else null,
                                primaryProgress = if (isPrimary) repoProgress else null
                            )
                        }

                        if (whitelistedSessions.size > 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                repeat(whitelistedSessions.size) { iteration ->
                                    val color = if (pagerState.currentPage == iteration)
                                        MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondary
                                    Box(modifier = Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(color))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item { SmallTitle(text = "Quick Access") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperArrow(title = stringResource(R.string.page_title_personalization),
                        summary = "Customize appearance", onClick = onOpenPersonalization)
                    SuperArrow(title = stringResource(R.string.title_parser_whitelist_manager),
                        summary = "Manage app whitelist", onClick = onOpenWhitelist)
                    SuperArrow(title = stringResource(R.string.title_app_settings),
                        summary = "App settings", onClick = onOpenSettings)
                    if (isDebugBuild) {
                        SuperArrow(title = "Debug", summary = "Debug Center", onClick = onOpenDebug)
                    }
                }
            }
        }

        if (updateReleaseInfo != null) {
            MiuixUpdateDialog(
                show = true,
                releaseInfo = updateReleaseInfo,
                onDismiss = onUpdateDismiss,
                onIgnore = onUpdateIgnore
            )
        }
    }
}

@Composable
private fun MiuixStatusPill(statusText: String, isActive: Boolean, onTap: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Canvas(modifier = Modifier.size(12.dp)) {
                drawCircle(color = if (isActive) StatusActive else StatusInactive)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = statusText, fontSize = MiuixTheme.textStyles.body1.fontSize,
                color = MiuixTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun MiuixIdleCard() {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 160.dp).padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_music_note), contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(48.dp).padding(bottom = 16.dp)
                )
                Text(text = stringResource(R.string.status_idle_waiting), fontSize = 18.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

@Composable
private fun MiuixMediaSessionCard(
    controller: MediaController,
    isPrimary: Boolean,
    primaryMetadata: LyricRepository.MediaInfo?,
    primaryLyric: String?,
    primaryLyricSource: String?,
    primaryAlbumArt: Bitmap?,
    primaryProgress: LyricRepository.PlaybackProgress?
) {
    var localMetadata by remember(controller) { mutableStateOf(controller.metadata) }
    var localPlaybackState by remember(controller) { mutableStateOf(controller.playbackState) }

    DisposableEffect(controller) {
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) { localPlaybackState = state }
            override fun onMetadataChanged(meta: MediaMetadata?) { localMetadata = meta }
        }
        controller.registerCallback(callback)
        onDispose { controller.unregisterCallback(callback) }
    }

    val title = if (isPrimary) primaryMetadata?.title
        else localMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val artist = if (isPrimary) primaryMetadata?.artist
        else localMetadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
    val albumArt = if (isPrimary) primaryAlbumArt
        else (localMetadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: localMetadata?.getBitmap(MediaMetadata.METADATA_KEY_ART))
    val lyric = if (isPrimary) primaryLyric else null
    val duration = if (isPrimary) primaryMetadata?.duration ?: 0L
        else localMetadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val position = if (isPrimary) primaryProgress?.position ?: 0L else localPlaybackState?.position ?: 0L
    val isPlaying = localPlaybackState?.state == PlaybackState.STATE_PLAYING ||
            localPlaybackState?.state == PlaybackState.STATE_BUFFERING

    val context = LocalContext.current

    // ── Palette accent from album art ──
    val dominantColor by produceState<Color?>(initialValue = null, key1 = albumArt) {
        value = albumArt?.let { bmp ->
            val palette = Palette.from(bmp).generate()
            val swatch = palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
            swatch?.rgb?.let { Color(it) }
        }
    }
    val animatedAccent by animateColorAsState(
        targetValue = dominantColor ?: MiuixTheme.colorScheme.primary,
        animationSpec = tween(600),
        label = "accent"
    )

    // ── Clean card — no background image, just palette accent on controls ──
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

            // Top Row: Album Art + Song Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt.asImageBitmap(), contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
                            .background(MiuixTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(R.drawable.ic_music_note), contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSecondary, modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title ?: "Unknown Title", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = artist ?: "Unknown Artist", fontSize = 16.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    val appName = remember(controller.packageName) {
                        ParserRuleHelper.getAppNameForPackage(context, controller.packageName)
                    }
                    // App name uses palette accent
                    Text(text = appName, fontSize = 12.sp, color = animatedAccent, maxLines = 1)
                    if (isPrimary && !primaryLyricSource.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "当前在线源: $primaryLyricSource", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Lyric — palette accent text
            Column(
                modifier = Modifier.height(90.dp).clickable(enabled = lyric != null) {
                    lyric?.let {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Lyric", it))
                        Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                if (lyric != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Lyric:", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.tap_to_copy_hint), fontSize = 10.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = lyric, fontSize = 18.sp, fontWeight = FontWeight.Normal,
                        minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = animatedAccent,  // palette accent
                        lineHeight = 24.sp)
                } else if (isPrimary) {
                    Text(text = "Lyric:", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Waiting for lyrics...", fontSize = 16.sp, fontStyle = FontStyle.Italic,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                } else {
                    Text(text = "Lyric:", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Lyrics unavailable", fontSize = 16.sp, fontStyle = FontStyle.Italic,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Slider — palette accent
            var isDragging by remember { mutableStateOf(false) }
            var dragProgress by remember { mutableFloatStateOf(0f) }
            val currentProgress = if (isDragging) dragProgress
                else if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

            Slider(
                value = currentProgress,
                onValueChange = { isDragging = true; dragProgress = it },
                onValueChangeFinished = {
                    if (duration > 0) controller.transportControls.seekTo((dragProgress * duration).toLong())
                    isDragging = false
                },
                modifier = Modifier.fillMaxWidth(),
                height = 12.dp,
                colors = SliderDefaults.sliderColors(foregroundColor = animatedAccent, thumbColor = animatedAccent)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Media Controls — play/pause circle uses palette accent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { controller.transportControls.skipToPrevious() }) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.ic_skip_previous), contentDescription = "Previous",
                        tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (isPlaying) controller.transportControls.pause()
                        else controller.transportControls.play()
                    },
                    modifier = Modifier.size(56.dp).background(animatedAccent, CircleShape)
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White, modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { controller.transportControls.skipToNext() }) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.ic_skip_next), contentDescription = "Next",
                        tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
