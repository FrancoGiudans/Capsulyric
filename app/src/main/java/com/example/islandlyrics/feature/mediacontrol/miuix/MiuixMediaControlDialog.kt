package com.example.islandlyrics.feature.mediacontrol.miuix

import com.example.islandlyrics.ui.miuix.MiuixBackHandler
import android.content.Context
import com.example.islandlyrics.R
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.service.MediaMonitorService
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.feature.main.MainActivity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import android.os.Build
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.delay
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SeekSyncThresholdMs = 250L
private const val SeekFallbackTimeoutMs = 4000L

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MiuixMediaControlDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeControllers by remember { mutableStateOf<List<MediaController>>(emptyList()) }
    var statusMessage by remember { mutableStateOf(context.getString(R.string.media_control_scanning)) }
    MiuixBackHandler(enabled = show) {
        onDismiss()
    }
    
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

    val blurEnabled = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE).getBoolean("card_blur_enabled", false) }
    
    var visibleState by remember { mutableStateOf(false) }
    LaunchedEffect(show) { 
        if (show) visibleState = true 
    }

    if (show || visibleState) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val view = androidx.compose.ui.platform.LocalView.current
            val density = LocalDensity.current
            
            // Runs synchronously after composition before draw
            SideEffect {
                val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                if (window != null) {
                    val horizontalMarginPx = with(density) { 16.dp.roundToPx() }
                    val bottomOffsetPx = with(density) { 32.dp.roundToPx() }
                    val cornerRadiusPx = with(density) { 28.dp.toPx() }
                    window.setGravity(android.view.Gravity.BOTTOM)
                    val params = window.attributes
                    params.width = (view.resources.displayMetrics.widthPixels - horizontalMarginPx * 2).coerceAtLeast(0)
                    params.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT
                    params.y = bottomOffsetPx
                    window.attributes = params

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
                            window.setBackgroundBlurRadius(if (blurEnabled) 140 else 0)
                        } catch (_: Exception) {}
                    }
                }
            }

            // Miuix scale/fade animation
            val scale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (visibleState && show) 1f else 0.8f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 300f),
                finishedListener = { if (!show) visibleState = false },
                label = "scale"
            )
            val alpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (visibleState && show) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(200),
                label = "alpha"
            )

            // Miuix Bottom Sheet Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        },
                    shape = RoundedCornerShape(28.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MiuixTheme.colorScheme.surface.copy(alpha = if (blurEnabled) 0.38f else 0.92f)
                    ),
                    elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Text(
                                text = stringResource(R.string.media_control_title),
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )
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
                        
                        MiuixMediaSessionLayout(
                            controller = controller, 
                            context = context,
                            isPrimary = isPrimary,
                            primaryLyric = if (isPrimary) repoLyric?.lyric else null,
                            primaryProgress = if (isPrimary) repoProgress else null,
                            onOpenApp = {
                                onDismiss()
                            }
                        )
                    }
                }
                
                if (whitelistedControllers.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(whitelistedControllers.size) { iteration ->
                            val active = pagerState.currentPage == iteration
                            val color = if (active) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.surfaceVariant
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
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.media_control_no_sessions), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }

            // Bottom Actions Row (Mi Play, Open App, Info)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(intent)
                        onDismiss()
                    },
                    modifier = Modifier.background(MiuixTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pill),
                        contentDescription = "Open Capsulyric",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (isHyperOsSupported) {
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent()
                                intent.component = android.content.ComponentName(
                                    "miui.systemui.plugin",
                                    "miui.systemui.miplay.MiPlayDetailActivity"
                                )
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.media_control_miplay_failed, e.message), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.background(MiuixTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_miplay),
                            contentDescription = "Mi Play",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { 
                        Toast.makeText(context, "$statusMessage (Whitelisted: ${whitelistedControllers.size})", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.background(MiuixTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = "Status",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
        }
    }
}

@Composable
fun MiuixMediaSessionLayout(
    controller: MediaController, 
    context: Context,
    isPrimary: Boolean,
    primaryLyric: String?,
    primaryProgress: LyricRepository.PlaybackProgress?,
    onOpenApp: (() -> Unit)? = null
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

    val dominantColor by produceState<Color?>(initialValue = null, key1 = albumArtBitmap) {
        value = albumArtBitmap?.let { bmp ->
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

    val openApp = {
        val displayName = appName.ifBlank { pkg }
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(pkg)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val sessionActivity = controller.sessionActivity

        when {
            sessionActivity != null -> {
                val sessionLaunchResult = runCatching { sessionActivity.send() }
                if (sessionLaunchResult.isFailure) {
                    if (launchIntent != null) {
                        val fallbackResult = runCatching { context.startActivity(launchIntent) }
                        if (fallbackResult.isSuccess) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.media_control_cannot_open_session_failed_fallback, displayName),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val reason = fallbackResult.exceptionOrNull()?.localizedMessage
                                ?: sessionLaunchResult.exceptionOrNull()?.localizedMessage
                                ?: "unknown"
                            Toast.makeText(
                                context,
                                context.getString(R.string.media_control_cannot_open_launch_failed, displayName, reason),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        val reason = sessionLaunchResult.exceptionOrNull()?.localizedMessage ?: "unknown"
                        Toast.makeText(
                            context,
                            context.getString(R.string.media_control_cannot_open_launch_failed, displayName, reason),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            launchIntent != null -> {
                val launchResult = runCatching { context.startActivity(launchIntent) }
                if (launchResult.isFailure) {
                    val reason = launchResult.exceptionOrNull()?.localizedMessage ?: "unknown"
                    Toast.makeText(
                        context,
                        context.getString(R.string.media_control_cannot_open_launch_failed, displayName, reason),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            else -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.media_control_cannot_open_no_entry, displayName),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        onOpenApp?.invoke()
        Unit
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface.copy(alpha = 0.28f),
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(132.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MiuixTheme.colorScheme.surface.copy(alpha = 0.12f),
                                MiuixTheme.colorScheme.surface.copy(alpha = 0.22f)
                            )
                        )
                    )
            )

            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
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
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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

                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artist,
                        fontSize = 16.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = appName, fontSize = 12.sp, color = animatedAccent, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.height(90.dp).clickable(enabled = primaryLyric != null || parsedLyricFromTitle != null) {
                    val effectiveLyric = if (isPrimary && !primaryLyric.isNullOrBlank()) primaryLyric else parsedLyricFromTitle
                    effectiveLyric?.let {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Lyric", it))
                        Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                val effectiveLyric = if (isPrimary && !primaryLyric.isNullOrBlank()) primaryLyric else parsedLyricFromTitle
                if (effectiveLyric != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Lyric:", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.tap_to_copy_hint),
                            fontSize = 10.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = effectiveLyric,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = animatedAccent,
                        lineHeight = 24.sp
                    )
                } else if (isPrimary) {
                    Text(text = "Lyric:", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Waiting for lyrics...",
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                } else {
                    Text(text = "Lyric:", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lyrics unavailable",
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Bar Section
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
        val currentProgress = if (duration > 0) (effectivePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        val displayProgress = if (isDragging) dragProgress else currentProgress

        Slider(
            value = displayProgress,
            onValueChange = { 
                isDragging = true
                dragProgress = it 
            },
            onValueChangeFinished = {
                isDragging = false
                if (duration > 0) {
                    seekOriginPosition = position
                    pendingSeekPosition = (dragProgress * duration).toLong()
                    pendingSeekStartedAt = SystemClock.elapsedRealtime()
                    controller.transportControls.seekTo(pendingSeekPosition)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            height = 12.dp,
            colors = SliderDefaults.sliderColors(foregroundColor = animatedAccent, thumbColor = animatedAccent)
        )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { controller.transportControls.skipToPrevious() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_previous),
                        contentDescription = "Previous",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (isPlaying) controller.transportControls.pause()
                        else controller.transportControls.play()
                    },
                    modifier = Modifier.size(56.dp).background(animatedAccent, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { controller.transportControls.skipToNext() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = "Next",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        }
    }
}
