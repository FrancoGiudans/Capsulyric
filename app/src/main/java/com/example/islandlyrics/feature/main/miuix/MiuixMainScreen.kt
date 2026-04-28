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
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
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
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val StatusActive = Color(0xFF4CAF50)
private val StatusInactive = Color(0xFFF44336)
private const val SeekSyncThresholdMs = 250L
private const val SeekFallbackTimeoutMs = 4000L

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
    bottomBar: @Composable () -> Unit = {},
    onBottomBarVisibilityChange: (Boolean) -> Unit = {},
    updateReleaseInfo: UpdateChecker.ReleaseInfo? = null,
    onUpdateDismiss: () -> Unit = {},
    onUpdateIgnore: (String) -> Unit = {}
) {
    val repo = remember { LyricRepository.getInstance() }
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    var dynamicThemeEnabled by remember { mutableStateOf(prefs.getBoolean("theme_dynamic_color", true)) }

    val repoPlaying by repo.isPlaying.observeAsState(false)
    val repoMetadata by repo.liveMetadata.observeAsState()
    val repoLyric by repo.liveLyric.observeAsState()
    val repoAlbumArt by repo.liveAlbumArt.observeAsState()
    val repoProgress by repo.liveProgress.observeAsState()
    val repoParsedLyrics by repo.liveParsedLyrics.observeAsState()

    var activeControllers by remember { mutableStateOf<List<MediaController>>(emptyList()) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_dynamic_color") {
                dynamicThemeEnabled = prefs.getBoolean("theme_dynamic_color", true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(listState) {
        var previousIndex = 0
        var previousOffset = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .map { (index, offset) ->
                val delta = when {
                    index != previousIndex -> (index - previousIndex) * 10_000 + (offset - previousOffset)
                    else -> offset - previousOffset
                }
                previousIndex = index
                previousOffset = offset
                delta
            }
            .distinctUntilChanged()
            .collect { delta ->
                when {
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 -> onBottomBarVisibilityChange(true)
                    delta > 6 -> onBottomBarVisibilityChange(false)
                    delta < -6 -> onBottomBarVisibilityChange(true)
                }
            }
    }

    val popupShowing = updateReleaseInfo != null

    LaunchedEffect(popupShowing) {
        if (popupShowing) {
            onBottomBarVisibilityChange(false)
        } else if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            onBottomBarVisibilityChange(true)
        }
    }

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
        !listenerEnabled -> stringResource(R.string.main_status_permission_required)
        hasActiveSession -> {
            if (repoPlaying || repoMetadata != null) {
                val rawPackage = repoLyric?.sourceApp ?: repoMetadata?.packageName ?: whitelistedSessions.firstOrNull()?.packageName
                val sourceName = rawPackage?.let { ParserRuleHelper.getAppNameForPackage(context, it) }
                    ?: stringResource(R.string.main_music_app_fallback)
                stringResource(R.string.main_status_connecting_fmt, sourceName)
            } else {
                stringResource(R.string.main_status_session_count_fmt, whitelistedSessions.size)
            }
        }
        !serviceConnected -> stringResource(R.string.main_status_service_disconnected)
        else -> stringResource(R.string.main_status_service_ready)
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
                },
                actions = {
                    if (isDebugBuild) {
                        IconButton(onClick = onOpenDebug, modifier = Modifier.padding(end = 12.dp)) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = "Debug Center",
                                tint = MiuixTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            )
        },
        bottomBar = bottomBar,
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 116.dp
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
                MiuixSectionLabel(
                    title = stringResource(R.string.main_now_playing_title),
                    subtitle = if (whitelistedSessions.isEmpty()) {
                        stringResource(R.string.main_now_playing_subtitle_idle)
                    } else {
                        stringResource(R.string.main_now_playing_subtitle_active)
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

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
                                dynamicThemeEnabled = dynamicThemeEnabled,
                                primaryMetadata = if (isPrimary) repoMetadata else null,
                                primaryLyric = if (isPrimary) repoLyric?.lyric else null,
                                primaryLyricSource = if (isPrimary) {
                                    formatPrimaryLyricSource(repoLyric?.apiPath, repoParsedLyrics?.sourceLabel)
                                } else {
                                    null
                                },
                                primaryAlbumArt = if (isPrimary) repoAlbumArt else null,
                                primaryProgress = if (isPrimary) repoProgress else null
                            )
                        }

                        if (whitelistedSessions.size > 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                repeat(whitelistedSessions.size) { iteration ->
                                    val active = pagerState.currentPage == iteration
                                    val color = if (active) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.secondary
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .height(8.dp)
                                            .width(if (active) 28.dp else 8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                }
                            }
                        }
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
private fun MiuixSectionLabel(
    title: String,
    subtitle: String,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
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
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 180.dp).padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.status_idle_waiting),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.main_idle_card_desc),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
private fun MiuixMediaSessionCard(
    controller: MediaController,
    isPrimary: Boolean,
    dynamicThemeEnabled: Boolean,
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
    val duration = if (isPrimary) primaryProgress?.duration ?: primaryMetadata?.duration ?: 0L
        else localMetadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val position = if (isPrimary) primaryProgress?.position ?: 0L else localPlaybackState?.position ?: 0L
    val isPlaying = localPlaybackState?.state == PlaybackState.STATE_PLAYING ||
            localPlaybackState?.state == PlaybackState.STATE_BUFFERING
    val sessionStateKey = buildString {
        append(controller.packageName)
        append('|')
        append(title.orEmpty())
        append('|')
        append(artist.orEmpty())
    }
    var pendingSeekPosition by remember(sessionStateKey) { mutableLongStateOf(-1L) }
    var pendingSeekStartedAt by remember(sessionStateKey) { mutableLongStateOf(0L) }
    var seekOriginPosition by remember(sessionStateKey) { mutableLongStateOf(-1L) }

    val context = LocalContext.current
    val appName = remember(controller.packageName) {
        ParserRuleHelper.getAppNameForPackage(context, controller.packageName)
    }
    val appIcon = remember(controller.packageName) {
        try {
            drawableToBitmap(context.packageManager.getApplicationIcon(controller.packageName))
        } catch (_: Exception) {
            null
        }
    }
    val openApp = {
        try {
            val sessionActivity = controller.sessionActivity
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(controller.packageName)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            when {
                sessionActivity != null -> {
                    runCatching {
                        sessionActivity.send()
                    }.getOrElse {
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            throw it
                        }
                    }
                }
                launchIntent != null -> {
                    context.startActivity(launchIntent)
                }
                else -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.media_control_cannot_open, appName.ifBlank { controller.packageName }),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.media_control_error_prefix, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    // ── Palette accent from album art ──
    val dominantColor by produceState<Color?>(initialValue = null, key1 = albumArt, key2 = dynamicThemeEnabled) {
        value = if (dynamicThemeEnabled) {
            albumArt?.let { bmp ->
                withContext(Dispatchers.Default) {
                    val palette = Palette.from(bmp).generate()
                    val swatch = palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
                    swatch?.rgb?.let { Color(it) }
                }
            }
        } else {
            null
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
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = openApp)
                ) {
                    if (albumArt != null) {
                        Image(
                            bitmap = albumArt.asImageBitmap(), contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                painter = painterResource(R.drawable.ic_music_note), contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSecondary, modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            androidx.compose.material3.Icon(
                                painter = painterResource(R.drawable.ic_music_note),
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
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
                    Text(text = appName, fontSize = 12.sp, color = animatedAccent, maxLines = 1)
                    if (isPrimary && !primaryLyricSource.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "当前歌词源: $primaryLyricSource", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1)
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
            LaunchedEffect(position, pendingSeekPosition, pendingSeekStartedAt, seekOriginPosition, isPlaying) {
                if (pendingSeekPosition < 0L) return@LaunchedEffect
                val expectedPendingPosition = if (isPlaying && pendingSeekStartedAt > 0L) {
                    (pendingSeekPosition + (SystemClock.elapsedRealtime() - pendingSeekStartedAt)).coerceAtMost(duration)
                } else {
                    pendingSeekPosition
                }
                val hasActuallyMovedFromOrigin = seekOriginPosition < 0L ||
                    abs(position - seekOriginPosition) > SeekSyncThresholdMs
                val isNearSeekTarget = abs(position - expectedPendingPosition) <= SeekSyncThresholdMs

                if (hasActuallyMovedFromOrigin && isNearSeekTarget) {
                    pendingSeekPosition = -1L
                    pendingSeekStartedAt = 0L
                    seekOriginPosition = -1L
                    return@LaunchedEffect
                }
                if (pendingSeekStartedAt <= 0L) return@LaunchedEffect
                val remaining = SeekFallbackTimeoutMs - (SystemClock.elapsedRealtime() - pendingSeekStartedAt)
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
            val currentProgress = if (isDragging) dragProgress
                else if (duration > 0) (effectivePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

            Slider(
                value = currentProgress,
                onValueChange = { isDragging = true; dragProgress = it },
                onValueChangeFinished = {
                    if (duration > 0) {
                        seekOriginPosition = position
                        pendingSeekPosition = (dragProgress * duration).toLong()
                        pendingSeekStartedAt = SystemClock.elapsedRealtime()
                        controller.transportControls.seekTo(pendingSeekPosition)
                    }
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

private fun formatPrimaryLyricSource(apiPath: String?, sourceLabel: String?): String? {
    val label = sourceLabel?.trim().orEmpty()
    if (label.isBlank()) return null
    return when (apiPath) {
        "Online API" -> "$label [在线]"
        "Online Cache" -> "$label [缓存]"
        else -> null
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
