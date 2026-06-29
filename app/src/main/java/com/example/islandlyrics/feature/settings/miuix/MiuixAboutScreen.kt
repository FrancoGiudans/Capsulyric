package com.example.islandlyrics.feature.settings.miuix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.islandlyrics.R
import com.example.islandlyrics.core.feed.CommunityFeed
import com.example.islandlyrics.core.feed.CommunityFeedRepository
import com.example.islandlyrics.core.feed.CommunityFeedStatus
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.feature.settings.CommunityDialogState
import com.example.islandlyrics.feature.settings.ReleaseDialogState
import com.example.islandlyrics.feature.update.miuix.MiuixUpdateDialog
import com.example.islandlyrics.feature.licenses.OpenSourceLicensesActivity
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.miuix.blur.LocalMiuixBlurBackdrop
import com.example.islandlyrics.ui.miuix.blur.LocalMiuixBlurEnabled
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.effects.MiuixAuroraBackground
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackIcon
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun MiuixAboutScreen(
    updateVersionText: String,
    updateCodenameText: String,
    updateBuildText: String,
    onCheckUpdate: () -> Unit,
    onViewCurrentVersionChangelog: () -> Unit,
    onBack: (() -> Unit)? = null,
    releaseDialogState: ReleaseDialogState? = null,
    onReleaseDialogDismiss: () -> Unit = {},
    onUpdateIgnore: (String) -> Unit = {},
    releaseLookupMessage: String? = null,
    onReleaseLookupMessageDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val backdrop = rememberLayerBackdrop()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val scrollProgress by remember {
        derivedStateOf {
            if (headerHeightPx <= 0) {
                0f
            } else if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset.toFloat() / headerHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    val prefs = remember(context) { AppPreferences.of(context) }
    var cardBlurEnabled by remember(prefs) {
        mutableStateOf(prefs.getBoolean(AppPreferences.Keys.CARD_BLUR_ENABLED, false))
    }
    androidx.compose.runtime.DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppPreferences.Keys.CARD_BLUR_ENABLED) {
                cardBlurEnabled = prefs.getBoolean(key, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val blurEnabled = cardBlurEnabled && isRenderEffectSupported()

    CompositionLocalProvider(
        LocalMiuixBlurBackdrop provides backdrop,
        LocalMiuixBlurEnabled provides blurEnabled
    ) {
        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = stringResource(R.string.community_about_title),
                    scrollBehavior = scrollBehavior,
                    color = MiuixTheme.colorScheme.surface.copy(alpha = if (scrollProgress >= 1f) 1f else 0f),
                    titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = scrollProgress),
                    defaultWindowInsetsPadding = true,
                    navigationIcon = {
                        IconButton(
                            onClick = { onBack?.invoke() ?: (context as? android.app.Activity)?.finish() },
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            MiuixBackIcon(contentDescription = "Back")
                        }
                    }
                )
            },
            popupHost = { MiuixPopupHost() },
            containerColor = MiuixTheme.colorScheme.surface
        ) { padding ->
            MiuixAboutContent(
                padding = padding,
                listState = listState,
                scrollBehavior = scrollBehavior,
                scrollProgress = scrollProgress,
                backdrop = backdrop,
                blurEnabled = blurEnabled,
                updateVersionText = updateVersionText,
                updateCodenameText = updateCodenameText,
                updateBuildText = updateBuildText,
                onCheckUpdate = onCheckUpdate,
                onViewCurrentVersionChangelog = onViewCurrentVersionChangelog,
                releaseDialogState = releaseDialogState,
                onReleaseDialogDismiss = onReleaseDialogDismiss,
                onUpdateIgnore = onUpdateIgnore,
                releaseLookupMessage = releaseLookupMessage,
                onReleaseLookupMessageDismiss = onReleaseLookupMessageDismiss,
                onHeaderHeightChanged = { headerHeightPx = it }
            )
        }
    }
}

@Composable
private fun MiuixAboutContent(
    padding: PaddingValues,
    listState: LazyListState,
    scrollBehavior: ScrollBehavior,
    scrollProgress: Float,
    backdrop: LayerBackdrop,
    blurEnabled: Boolean,
    updateVersionText: String,
    updateCodenameText: String,
    updateBuildText: String,
    onCheckUpdate: () -> Unit,
    onViewCurrentVersionChangelog: () -> Unit,
    releaseDialogState: ReleaseDialogState?,
    onReleaseDialogDismiss: () -> Unit,
    onUpdateIgnore: (String) -> Unit,
    releaseLookupMessage: String?,
    onReleaseLookupMessageDismiss: () -> Unit,
    onHeaderHeightChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val devModeEnabled by LyricRepository.getInstance().devModeEnabled.observeAsState(false)
    val offlineModeEnabled = remember { OfflineModeManager.isEnabled(context) }
    var autoUpdateEnabled by remember { mutableStateOf(UpdateChecker.isAutoUpdateEnabled(context)) }
    var currentChannel by remember { mutableStateOf(UpdateChecker.getUpdateChannel(context)) }
    var experimentUpdatesEnabled by remember { mutableStateOf(LabFeatureManager.isExperimentUpdatesEnabled(context)) }
    var feedSourcePriority by remember { mutableStateOf(LabFeatureManager.getFeedSourcePriority(context)) }
    var communityFeed by remember { mutableStateOf<CommunityFeed?>(null) }
    var communityFeedLoaded by remember { mutableStateOf(false) }
    var communityDialogState by remember { mutableStateOf<CommunityDialogState?>(null) }
    var showFeedbackPopup by remember { mutableStateOf(false) }
    var devStepCount by remember { mutableIntStateOf(0) }

    var headerHeightDp by remember { mutableStateOf(300.dp) }
    var headerAreaBottomY by remember { mutableFloatStateOf(0f) }
    var iconBottomY by remember { mutableFloatStateOf(0f) }
    var titleBottomY by remember { mutableFloatStateOf(0f) }
    var versionBottomY by remember { mutableFloatStateOf(0f) }
    var initialHeaderAreaBottomY by remember { mutableFloatStateOf(0f) }
    var iconProgress by remember { mutableFloatStateOf(0f) }
    var titleProgress by remember { mutableFloatStateOf(0f) }
    var versionProgress by remember { mutableFloatStateOf(0f) }

    val navigationBarBottomPadding = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val listPadding = PaddingValues(
        start = padding.calculateStartPadding(layoutDirection),
        top = padding.calculateTopPadding(),
        end = padding.calculateEndPadding(layoutDirection),
        bottom = padding.calculateBottomPadding() + navigationBarBottomPadding + 24.dp
    )
    val versionInfoText = "$updateVersionText\n$updateCodenameText\n$updateBuildText"
    val copiedText = stringResource(R.string.toast_copied)
    val devModeAlreadyEnabledText = stringResource(R.string.toast_dev_mode_already_enabled)
    val devModeStepsFormat = stringResource(R.string.toast_dev_mode_steps)
    val devModeEnabledText = stringResource(R.string.toast_dev_mode_enabled)

    val cardBlurColors = aboutCardBlurColors(isDark = isDark)
    val logoBlendColors = aboutLogoBlendColors(isDark = isDark)

    fun copyVersionInfo() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Capsulyric version", versionInfoText))
        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
    }

    fun handleDevModeTap() {
        if (devModeEnabled) {
            Toast.makeText(context, devModeAlreadyEnabledText, Toast.LENGTH_SHORT).show()
            return
        }
        devStepCount++
        if (devStepCount in 3..6) {
            Toast.makeText(context, String.format(devModeStepsFormat, 7 - devStepCount), Toast.LENGTH_SHORT).show()
        } else if (devStepCount == 7) {
            LyricRepository.getInstance().setDevMode(context, true)
            Toast.makeText(context, devModeEnabledText, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(offlineModeEnabled, feedSourcePriority) {
        communityFeedLoaded = false
        if (offlineModeEnabled) {
            communityFeed = null
            communityFeedLoaded = true
        } else {
            communityFeed = CommunityFeedRepository.fetchFeed(context)
            communityFeedLoaded = true
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemScrollOffset }
            .collect { offset ->
                if (listState.firstVisibleItemIndex > 0) {
                    iconProgress = 1f
                    titleProgress = 1f
                    versionProgress = 1f
                    return@collect
                }
                if (initialHeaderAreaBottomY == 0f && headerAreaBottomY > 0f) {
                    initialHeaderAreaBottomY = headerAreaBottomY
                }
                val referenceBottom = if (initialHeaderAreaBottomY > 0f) initialHeaderAreaBottomY else headerAreaBottomY
                val stage1 = (referenceBottom - versionBottomY).coerceAtLeast(1f)
                val stage2 = (versionBottomY - titleBottomY).coerceAtLeast(1f)
                val stage3 = (titleBottomY - iconBottomY).coerceAtLeast(1f)
                val offsetFloat = offset.toFloat()
                val versionDelay = stage1 * 0.5f

                versionProgress = ((offsetFloat - versionDelay) / (stage1 - versionDelay).coerceAtLeast(1f)).coerceIn(0f, 1f)
                titleProgress = ((offsetFloat - stage1) / stage2).coerceIn(0f, 1f)
                iconProgress = ((offsetFloat - stage1 - stage2) / stage3).coerceIn(0f, 1f)
            }
    }

    MiuixAuroraBackground(
        dynamicBackground = true,
        effectBackground = true,
        isDark = isDark,
        modifier = Modifier.fillMaxSize(),
        backgroundModifier = Modifier.layerBackdrop(backdrop),
        alpha = { 1f - scrollProgress },
    ) {
        HeaderContent(
            backdrop = backdrop,
            blurEnabled = blurEnabled,
            logoBlendColors = logoBlendColors,
            versionInfoText = versionInfoText,
            onVersionClick = ::copyVersionInfo,
            onHeightChanged = { headerHeightDp = it },
            onIconBottomChanged = { if (iconBottomY == 0f) iconBottomY = it },
            onTitleBottomChanged = { if (titleBottomY == 0f) titleBottomY = it },
            onVersionBottomChanged = { if (versionBottomY == 0f) versionBottomY = it },
            iconProgress = iconProgress,
            titleProgress = titleProgress,
            versionProgress = versionProgress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = listPadding.calculateTopPadding() + 52.dp,
                    start = 24.dp,
                    end = 24.dp
                )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = listPadding,
            overscrollEffect = null
        ) {
            item(key = "header_spacer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeightDp + 52.dp + 126.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { handleDevModeTap() }
                        }
                        .onSizeChanged { onHeaderHeightChanged(it.height) }
                        .onGloballyPositioned { coordinates ->
                            headerAreaBottomY = coordinates.positionInWindow().y + coordinates.size.height
                        }
                )
            }

            if (!offlineModeEnabled) {
                item(key = "community") {
                    SmallTitle(text = stringResource(R.string.settings_community_header))
                    FrostedAboutCard(
                        backdrop = backdrop,
                        blurEnabled = blurEnabled,
                        blurColors = cardBlurColors
                    ) {
                        CommunitySection(
                            offlineModeEnabled = offlineModeEnabled,
                            feedSourcePriority = feedSourcePriority,
                            onFeedSourcePriorityChange = { source ->
                                feedSourcePriority = source
                                LabFeatureManager.setFeedSourcePriority(context, source)
                            },
                            communityFeed = communityFeed,
                            communityFeedLoaded = communityFeedLoaded,
                            onCommunityItemClick = { communityDialogState = it }
                        )
                    }
                }
            }

            item(key = "about") {
                SmallTitle(text = stringResource(R.string.about_title))
                FrostedAboutCard(
                    backdrop = backdrop,
                    blurEnabled = blurEnabled,
                    blurColors = cardBlurColors
                ) {
                    AboutSection(
                        showFeedbackPopup = showFeedbackPopup,
                        onToggleFeedback = { showFeedbackPopup = !showFeedbackPopup },
                        versionInfoText = versionInfoText,
                        onCopyVersion = ::copyVersionInfo
                    )
                }
            }

            if (!offlineModeEnabled) {
                item(key = "update") {
                    SmallTitle(text = stringResource(R.string.update_check_title))
                    FrostedAboutCard(
                        backdrop = backdrop,
                        blurEnabled = blurEnabled,
                        blurColors = cardBlurColors
                    ) {
                        UpdateSection(
                            autoUpdateEnabled = autoUpdateEnabled,
                            onAutoUpdateChange = {
                                autoUpdateEnabled = it
                                UpdateChecker.setAutoUpdateEnabled(context, it)
                            },
                            currentChannel = currentChannel,
                            experimentUpdatesEnabled = experimentUpdatesEnabled,
                            onChannelChange = {
                                currentChannel = it
                                UpdateChecker.setUpdateChannel(context, it)
                                experimentUpdatesEnabled = LabFeatureManager.isExperimentUpdatesEnabled(context)
                            },
                            onCheckUpdate = onCheckUpdate,
                            onViewCurrentVersionChangelog = onViewCurrentVersionChangelog
                        )
                    }
                }
            }

            item(key = "licenses") {
                SmallTitle(text = stringResource(R.string.open_source_licenses_header))
                FrostedAboutCard(
                    backdrop = backdrop,
                    blurEnabled = blurEnabled,
                    blurColors = cardBlurColors
                ) {
                    BasicComponent(
                        title = stringResource(R.string.open_source_licenses_title),
                        summary = stringResource(R.string.open_source_licenses_summary),
                        onClick = {
                            context.startActivity(Intent(context, OpenSourceLicensesActivity::class.java))
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    communityDialogState?.let { dialogState ->
        CommunityDetailsDialog(
            state = dialogState,
            onDismiss = { communityDialogState = null },
            onOpen = {
                if (dialogState.item.hasUrl) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, dialogState.item.url.toUri()))
                }
                communityDialogState = null
            }
        )
    }

    releaseDialogState?.let { dialogState ->
        MiuixUpdateDialog(
            show = true,
            releaseInfo = dialogState.releaseInfo,
            mode = dialogState.mode,
            onDismiss = onReleaseDialogDismiss,
            onIgnore = onUpdateIgnore
        )
    }

    if (releaseLookupMessage != null) {
        MiuixBlurDialog(
            title = stringResource(R.string.update_current_version_changelog_unavailable_title),
            summary = releaseLookupMessage,
            show = true,
            onDismissRequest = onReleaseLookupMessageDismiss
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.update_close),
                        onClick = onReleaseLookupMessageDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderContent(
    backdrop: LayerBackdrop,
    blurEnabled: Boolean,
    logoBlendColors: List<BlendColorEntry>,
    versionInfoText: String,
    onVersionClick: () -> Unit,
    onHeightChanged: (androidx.compose.ui.unit.Dp) -> Unit,
    onIconBottomChanged: (Float) -> Unit,
    onTitleBottomChanged: (Float) -> Unit,
    onVersionBottomChanged: (Float) -> Unit,
    iconProgress: Float,
    titleProgress: Float,
    versionProgress: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Column(
        modifier = modifier.onSizeChanged { size ->
            with(density) { onHeightChanged(size.height.toDp()) }
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    alpha = 1f - iconProgress
                    scaleX = 1f - iconProgress * 0.05f
                    scaleY = 1f - iconProgress * 0.05f
                }
                .onGloballyPositioned { coordinates ->
                    onIconBottomChanged(coordinates.positionInWindow().y + coordinates.size.height)
                },
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_monochrome),
                contentDescription = "Capsulyric",
                modifier = Modifier
                    .requiredSize(160.dp)
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 200f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = BlurColors(blendColors = logoBlendColors),
                        contentBlendMode = BlendMode.DstIn,
                        enabled = blurEnabled
                    )
            )
        }

        Text(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 5.dp)
                .graphicsLayer {
                    alpha = 1f - titleProgress
                    scaleX = 1f - titleProgress * 0.05f
                    scaleY = 1f - titleProgress * 0.05f
                }
                .onGloballyPositioned { coordinates ->
                    onTitleBottomChanged(coordinates.positionInWindow().y + coordinates.size.height)
                }
                .textureBlur(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(16.dp),
                    blurRadius = 150f,
                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                    colors = BlurColors(blendColors = logoBlendColors),
                    contentBlendMode = BlendMode.DstIn,
                    enabled = blurEnabled
                ),
            text = stringResource(R.string.app_name),
            fontSize = 35.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onVersionClick() }
                .graphicsLayer {
                    alpha = 1f - versionProgress
                    scaleX = 1f - versionProgress * 0.05f
                    scaleY = 1f - versionProgress * 0.05f
                }
                .onGloballyPositioned { coordinates ->
                    onVersionBottomChanged(coordinates.positionInWindow().y + coordinates.size.height)
                },
            text = versionInfoText,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CommunitySection(
    offlineModeEnabled: Boolean,
    feedSourcePriority: String,
    onFeedSourcePriorityChange: (String) -> Unit,
    communityFeed: CommunityFeed?,
    communityFeedLoaded: Boolean,
    onCommunityItemClick: (CommunityDialogState) -> Unit
) {
    val announcementSectionTitle = stringResource(R.string.community_announcement_title)
    val pollSectionTitle = stringResource(R.string.community_poll_title)

    if (!offlineModeEnabled) {
        val feedSourceOptions = listOf(
            LabFeatureManager.FEED_SOURCE_GITHUB,
            LabFeatureManager.FEED_SOURCE_GITEE
        )
        val feedSourceIndex = feedSourceOptions.indexOf(feedSourcePriority).takeIf { it >= 0 } ?: 0

        SuperDropdown(
            title = stringResource(R.string.diag_lab_feed_source_title),
            summary = stringResource(R.string.diag_lab_feed_source_desc),
            items = listOf(
                stringResource(R.string.diag_lab_feed_source_github),
                stringResource(R.string.diag_lab_feed_source_gitee)
            ),
            selectedIndex = feedSourceIndex,
            onSelectedIndexChange = { index ->
                onFeedSourcePriorityChange(feedSourceOptions[index])
            }
        )
    }

    if (!communityFeedLoaded) {
        BasicComponent(
            title = stringResource(R.string.community_loading_title),
            summary = stringResource(R.string.community_loading_desc)
        )
    } else {
        communityFeed?.announcements?.forEach { announcement ->
            CommunityArrowItem(
                title = announcementSectionTitle,
                item = announcement,
                fallbackSummary = stringResource(R.string.community_open_in_browser),
                onClick = { onCommunityItemClick(CommunityDialogState(announcementSectionTitle, announcement)) }
            )
        }

        communityFeed?.polls?.forEach { poll ->
            CommunityArrowItem(
                title = pollSectionTitle,
                item = poll,
                fallbackSummary = stringResource(R.string.community_open_in_browser),
                onClick = { onCommunityItemClick(CommunityDialogState(pollSectionTitle, poll)) }
            )
        }

        if (communityFeed?.hasContent != true) {
            BasicComponent(
                title = if (communityFeed?.status == CommunityFeedStatus.UNAVAILABLE) {
                    stringResource(R.string.community_unavailable_title)
                } else {
                    stringResource(R.string.community_empty_title)
                },
                summary = if (communityFeed?.status == CommunityFeedStatus.UNAVAILABLE) {
                    stringResource(R.string.community_unavailable_desc)
                } else {
                    stringResource(R.string.community_empty_desc)
                }
            )
        }
    }
}

@Composable
private fun AboutSection(
    showFeedbackPopup: Boolean,
    onToggleFeedback: () -> Unit,
    versionInfoText: String,
    onCopyVersion: () -> Unit
) {
    val context = LocalContext.current
    BasicComponent(
        title = stringResource(R.string.settings_feedback),
        summary = stringResource(R.string.summary_feedback),
        endActions = {
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (showFeedbackPopup) 90f else 0f
                }
            )
        },
        onClick = onToggleFeedback
    )
    AnimatedVisibility(
        visible = showFeedbackPopup,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column {
            BasicComponent(
                title = stringResource(R.string.dialog_feedback_github),
                summary = stringResource(R.string.dialog_feedback_github_desc),
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/FrancoGiudans/Capsulyric/issues/new?template=bug_report.yml".toUri()))
                }
            )
            BasicComponent(
                title = stringResource(R.string.dialog_feedback_wps),
                summary = stringResource(R.string.dialog_feedback_wps_desc),
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://f.wps.cn/g/qACKW9I3/".toUri()))
                }
            )
        }
    }

    BasicComponent(
        title = stringResource(R.string.settings_about_github),
        summary = stringResource(R.string.summary_github),
        onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FrancoGiudans/Capsulyric")))
        }
    )

    BasicComponent(
        title = stringResource(R.string.about_version),
        summary = versionInfoText,
        onClick = onCopyVersion
    )
}

@Composable
private fun UpdateSection(
    autoUpdateEnabled: Boolean,
    onAutoUpdateChange: (Boolean) -> Unit,
    currentChannel: String,
    experimentUpdatesEnabled: Boolean,
    onChannelChange: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onViewCurrentVersionChangelog: () -> Unit
) {
    BasicComponent(
        title = stringResource(R.string.settings_auto_update),
        summary = stringResource(R.string.settings_auto_update_desc),
        endActions = {
            Switch(
                checked = autoUpdateEnabled,
                onCheckedChange = onAutoUpdateChange
            )
        },
        onClick = { onAutoUpdateChange(!autoUpdateEnabled) }
    )

    BasicComponent(
        title = stringResource(R.string.settings_prerelease_update),
        summary = currentChannel,
        onClick = {
            val channels = buildList {
                add(UpdateChecker.CHANNEL_STABLE)
                add(UpdateChecker.CHANNEL_PREVIEW)
                if (experimentUpdatesEnabled) add(UpdateChecker.CHANNEL_EXPERIMENT)
            }
            val currentIndex = channels.indexOf(currentChannel).takeIf { it >= 0 } ?: 0
            onChannelChange(channels[(currentIndex + 1) % channels.size])
        }
    )

    BasicComponent(
        title = stringResource(R.string.update_check_title),
        summary = stringResource(R.string.summary_check_updates_now),
        onClick = onCheckUpdate
    )

    BasicComponent(
        title = stringResource(R.string.update_current_version_changelog_title_short),
        summary = stringResource(R.string.summary_view_current_version_changelog),
        onClick = onViewCurrentVersionChangelog
    )
}

@Composable
private fun FrostedAboutCard(
    backdrop: LayerBackdrop,
    blurEnabled: Boolean,
    blurColors: BlurColors,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .then(
                if (blurEnabled) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = shape,
                        blurRadius = 60f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = blurColors
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.defaultColors(
            color = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        content()
    }
}

private fun aboutCardBlurColors(isDark: Boolean): BlurColors {
    val blend = if (isDark) {
        listOf(
            BlendColorEntry(Color(0x757A7A7A), BlurBlendMode.Luminosity),
        )
    } else {
        listOf(
            BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
            BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
        )
    }
    return BlurColors(blendColors = blend)
}

private fun aboutLogoBlendColors(isDark: Boolean): List<BlendColorEntry> {
    return if (isDark) {
        listOf(
            BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
            BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
        )
    } else {
        listOf(
            BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
            BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
        )
    }
}
