@file:Suppress("UnusedMaterial3ScaffoldPaddingParameter")

package com.example.islandlyrics.feature.oobe.miuix

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.widget.Toast
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.islandlyrics.R
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.SettingsBackupManager
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.feature.customsettings.CustomSettingsActivity
import com.example.islandlyrics.feature.faq.FAQActivity
import com.example.islandlyrics.feature.faq.material.FormattedText
import com.example.islandlyrics.feature.parserrule.ParserRuleActivity
import com.example.islandlyrics.feature.settings.SettingsActivity
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurTopAppBar
import com.example.islandlyrics.ui.miuix.effects.miuixPageScroll
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference as SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MiuixOobeScreen(
    onFinish: () -> Unit,
    onImportAndFinish: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupImportSuccessFormat = stringResource(R.string.settings_backup_import_success)
    val backupImportSuccessWithCacheFormat = stringResource(R.string.settings_backup_import_success_with_cache)
    val backupImportFailedText = stringResource(R.string.settings_backup_import_failed)
    var currentStep by remember { mutableIntStateOf(0) }
    var showRuleGuideDialog by remember { mutableStateOf(false) }
    val appIcon = remember { appIconBitmap(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission states lifted for cross-step access
    var listenerGranted by remember { mutableStateOf(checkNotificationListener(context)) }
    var postGranted by remember { mutableStateOf(checkPostNotification(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                listenerGranted = checkNotificationListener(context)
                postGranted = checkPostNotification(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val guides = listOf(
        AppGuide(
            name = stringResource(R.string.oobe_tab_qq),
            guide = stringResource(R.string.oobe_guide_qq)
        ),
        AppGuide(
            name = stringResource(R.string.oobe_tab_netease),
            guide = stringResource(R.string.oobe_guide_netease)
        ),
        AppGuide(
            name = stringResource(R.string.oobe_tab_xiaomi),
            guide = stringResource(R.string.oobe_guide_xiaomi)
        )
    )
    val importSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = SettingsBackupManager.importFromUri(context, uri)
            if (result.success) {
                // Auto-resolve parser rule conflicts: overwrite all (OOBE backup restore)
                if (result.parserConflicts.isNotEmpty()) {
                    SettingsBackupManager.resolveParserConflicts(
                        context,
                        readParserJsonFromImportUri(context, uri),
                        keepExistingPkgs = emptySet()  // overwrite all with imported rules
                    )
                }
                // Apply imported language before finishing OOBE
                val prefs = AppPreferences.of(context)
                val langCode = prefs.getString(AppPreferences.Keys.LANGUAGE_CODE, "") ?: ""
                if (langCode.isNotEmpty()) {
                    ThemeHelper.setLanguage(context, langCode)
                }
                val message = if (result.lyricCacheCount > 0) {
                    String.format(Locale.getDefault(), backupImportSuccessWithCacheFormat, result.importedCount, result.lyricCacheCount)
                } else {
                    String.format(Locale.getDefault(), backupImportSuccessFormat, result.importedCount)
                }
                onImportAndFinish(message)
            } else {
                Toast.makeText(context, backupImportFailedText, Toast.LENGTH_SHORT).show()
            }
        }
    }


    BackHandler(enabled = currentStep > 0 || showRuleGuideDialog) {
        when {
            showRuleGuideDialog -> showRuleGuideDialog = false
            currentStep > 0 -> currentStep -= 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackgroundColor())
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    // Forward: new page slides in from right, old slides out to left
                    (slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(430, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(430, easing = FastOutSlowInEasing)))
                        .togetherWith(
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(430, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(430, easing = FastOutSlowInEasing))
                        )
                } else {
                    // Backward: old page slides back in from left, current slides out to right
                    (slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(380, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(380, easing = FastOutSlowInEasing)))
                        .togetherWith(
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(380, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(380, easing = FastOutSlowInEasing))
                        )
                }
            },
            label = "oobePage"
        ) { step ->
            when (step) {
                0 -> LandingPage(
                appIcon = appIcon,
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.oobe_welcome_subtitle),
                actionText = stringResource(R.string.oobe_next),
                onAction = { currentStep = 1 },
                primary = true
            )

            1 -> StandardStepPage(
                title = stringResource(R.string.oobe_step_features),
                showBack = true,
                actionText = stringResource(R.string.oobe_next),
                onBack = { currentStep = 0 },
                onAction = { currentStep = 2 }
            ) {
                FeaturesStepBody()
            }

            2 -> StandardStepPage(
                title = stringResource(R.string.oobe_step_permissions),
                showBack = true,
                actionText = stringResource(R.string.oobe_next),
                actionEnabled = listenerGranted && postGranted,
                onBack = { currentStep = 1 },
                onAction = { currentStep = 3 }
            ) {
                PermissionsStepBody(
                    listenerGranted = listenerGranted,
                    postGranted = postGranted,
                    onListenerGrantedChange = { listenerGranted = it },
                    onPostGrantedChange = { postGranted = it }
                )
            }

            3 -> StandardStepPage(
                title = stringResource(R.string.oobe_step_restore),
                showBack = true,
                actionText = stringResource(R.string.oobe_restore_fresh_start),
                onBack = { currentStep = 2 },
                onAction = { currentStep = 4 }
            ) {
                RestoreStepBody(
                    onImportBackup = {
                        importSettingsLauncher.launch(arrayOf("application/zip", "application/json", "*/*"))
                    }
                )
            }

            4 -> StandardStepPage(
                title = stringResource(R.string.oobe_step_apps),
                showBack = true,
                actionText = stringResource(R.string.oobe_next),
                onBack = { currentStep = 3 },
                onAction = { currentStep = OOBE_LAST_STEP },
                overlay = {
                    if (showRuleGuideDialog) {
                        RuleGuideDialog(
                            onDismiss = { showRuleGuideDialog = false },
                            onOpenParserRules = {
                                showRuleGuideDialog = false
                                context.startActivity(Intent(context, ParserRuleActivity::class.java))
                            }
                        )
                    }
                }
            ) {
                AppSetupStepBody(
                    guides = guides,
                    onOpenSettings = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    onOpenCustomSettings = {
                        context.startActivity(Intent(context, CustomSettingsActivity::class.java))
                    },
                    onOpenParserRules = {
                        context.startActivity(Intent(context, ParserRuleActivity::class.java))
                    },
                    onOpenFaq = {
                        context.startActivity(Intent(context, FAQActivity::class.java))
                    },
                    onOpenRuleGuide = { showRuleGuideDialog = true }
                )
            }

            else -> LandingPage(
                appIcon = appIcon,
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.oobe_finish_subtitle),
                actionText = stringResource(R.string.oobe_finish_start),
                onAction = onFinish,
                primary = true,
                showBack = true,
                onBack = { currentStep = 4 }
            )
        }
    }
    }
}

@Composable
private fun LandingPage(
    appIcon: Bitmap,
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit,
    primary: Boolean,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackgroundColor())
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (showBack && onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = appIcon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(104.dp)
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = title,
                fontSize = 42.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = subtitle,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = onAction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            minHeight = 60.dp,
            cornerRadius = 30.dp,
            colors = if (primary) {
                ButtonDefaults.buttonColorsPrimary()
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(
                text = actionText,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun StandardStepPage(
    title: String,
    showBack: Boolean,
    actionText: String,
    onBack: () -> Unit,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                color = pageBackgroundColor(),
                navigationIcon = {
                    if (showBack) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            )
        },
        containerColor = pageBackgroundColor(),
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBackgroundColor())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .miuixPageScroll(scrollBehavior),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 12.dp,
                        bottom = 12.dp
                    )
                ) {
                    item { content() }
                }

                Button(
                    onClick = onAction,
                    enabled = actionEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = if (!actionEnabled) 4.dp else 8.dp,
                            bottom = padding.calculateBottomPadding() + 12.dp
                        ),
                    minHeight = 60.dp,
                    cornerRadius = 30.dp,
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(
                        text = actionText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (!actionEnabled) {
                    Text(
                        text = stringResource(R.string.oobe_perm_required_hint),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                }
            }

            overlay()
        }
    }
}

@Composable
private fun FeaturesStepBody() {
    val systemStatus = remember { checkSystemStatus() }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (systemStatus != SystemStatus.FullSupport) {
            FilledCard {
                Text(
                    text = when (systemStatus) {
                        SystemStatus.CommunityVerified -> stringResource(R.string.oobe_warning_community_verified)
                        SystemStatus.UntestedA16 -> stringResource(R.string.oobe_warning_untested_system)
                        SystemStatus.SuperIslandOnly -> stringResource(R.string.oobe_warning_super_island_limited)
                        SystemStatus.Unsupported -> stringResource(R.string.oobe_error_unsupported_device)
                    },
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        FilledCard {
            FeatureBlock(
                title = stringResource(R.string.oobe_feat_island_title),
                summary = stringResource(R.string.oobe_feat_island_desc)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 2.dp))
            FeatureBlock(
                title = stringResource(R.string.oobe_feat_source_title),
                summary = stringResource(R.string.oobe_feat_source_desc)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 2.dp))
            FeatureBlock(
                title = stringResource(R.string.oobe_feat_style_title),
                summary = stringResource(R.string.oobe_feat_style_desc)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        FilledCard {
            Text(
                text = stringResource(R.string.oobe_privacy_notice),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
@SuppressLint("BatteryLife")
private fun PermissionsStepBody(
    listenerGranted: Boolean,
    postGranted: Boolean,
    onListenerGrantedChange: (Boolean) -> Unit,
    onPostGrantedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var batteryGranted by remember { mutableStateOf(checkBatteryOptimization(context)) }
    val autostartIntent = remember { RomUtils.getAutostartPermissionIntent(context) }
    val showAutostart = RomUtils.isHeavySkin() && autostartIntent != null
    var showListenerScopeDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                onListenerGrantedChange(checkNotificationListener(context))
                onPostGrantedChange(checkPostNotification(context))
                batteryGranted = checkBatteryOptimization(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        FilledArrowCard {
            SuperArrow(
                title = stringResource(R.string.oobe_perm_listener_title),
                summary = permissionSummaryEnhanced(
                    granted = listenerGranted,
                    text = stringResource(R.string.oobe_perm_listener_desc),
                    isRequired = true
                ),
                endActions = {
                    if (listenerGranted) {
                        PermissionGrantedCheckmark()
                    }
                },
                onClick = {
                    if (!listenerGranted) {
                        showListenerScopeDialog = true
                    } else {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            SuperArrow(
                title = stringResource(R.string.oobe_perm_post_title),
                summary = permissionSummaryEnhanced(
                    granted = postGranted,
                    text = stringResource(R.string.oobe_perm_post_desc),
                    isRequired = true
                ),
                endActions = {
                    if (postGranted) {
                        PermissionGrantedCheckmark()
                    }
                },
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
            if (showAutostart) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                SuperArrow(
                    title = stringResource(R.string.oobe_perm_autostart_title),
                    summary = stringResource(R.string.oobe_perm_autostart_desc),
                    onClick = { runCatching { context.startActivity(autostartIntent) } }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            SuperArrow(
                title = stringResource(R.string.oobe_perm_battery_title),
                summary = permissionSummaryEnhanced(
                    granted = batteryGranted,
                    text = stringResource(R.string.oobe_perm_battery_desc),
                    isRequired = false
                ),
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                }
            )
        }
    }

    // Privacy / Scope Dialog for Notification Listener
    MiuixBlurDialog(
        show = showListenerScopeDialog,
        title = stringResource(R.string.oobe_perm_listener_title),
        onDismissRequest = { showListenerScopeDialog = false }
    ) {
        androidx.compose.material3.Text(
            text = stringResource(R.string.oobe_perm_listener_scope),
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = stringResource(R.string.dialog_btn_cancel),
                onClick = { showListenerScopeDialog = false },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(
                    textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(
                text = stringResource(R.string.dialog_btn_understand),
                onClick = {
                    showListenerScopeDialog = false
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
private fun AppSetupStepBody(
    guides: List<AppGuide>,
    onOpenSettings: () -> Unit,
    onOpenCustomSettings: () -> Unit,
    onOpenParserRules: () -> Unit,
    onOpenFaq: () -> Unit,
    onOpenRuleGuide: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = stringResource(R.string.oobe_step_apps))
        FilledArrowCard {
            guides.forEachIndexed { index, guide ->
                GuideExpandableItem(
                    guide = guide,
                    initiallyExpanded = index == 0
                )
                if (index < guides.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SmallTitle(text = stringResource(R.string.oobe_online_alt_title))
        FilledCard {
            Text(
                text = stringResource(R.string.oobe_online_alt_desc),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))
            FormattedText(
                text = stringResource(R.string.oobe_online_alt_guide),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14f
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        FilledArrowCard {
            SuperArrow(
                title = stringResource(R.string.title_app_settings),

                startAction = {
                    PreferenceIcon(imageVector = Icons.Filled.Settings)
                },
                onClick = onOpenSettings
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            SuperArrow(
                title = stringResource(R.string.page_title_personalization),
                startAction = {
                    PreferenceIcon(imageVector = Icons.Filled.Palette)
                },
                onClick = onOpenCustomSettings
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            SuperArrow(
                title = stringResource(R.string.title_parser_whitelist_manager),
                startAction = {
                    PreferenceIcon(imageVector = Icons.Filled.Tune)
                },
                onClick = onOpenParserRules
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            SuperArrow(
                title = stringResource(R.string.faq_title),
                startAction = {
                    PreferenceIcon(imageVector = Icons.Filled.Settings)
                },
                onClick = onOpenFaq
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        FilledArrowCard {
            SuperArrow(
                title = stringResource(R.string.oobe_app_not_in_list),
                summary = stringResource(R.string.oobe_add_rule_guide_title),
                onClick = onOpenRuleGuide
            )
        }
    }
}

@Composable
private fun RestoreStepBody(
    onImportBackup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = stringResource(R.string.oobe_step_restore))
        FilledCard {
            Text(
                text = stringResource(R.string.oobe_restore_intro_desc),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        FilledArrowCard {
            SuperArrow(
                title = stringResource(R.string.settings_backup_import),
                summary = stringResource(R.string.oobe_restore_import_desc),
                onClick = onImportBackup
            )
        }
    }
}

@Composable
private fun FilledCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface,
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            content = content
        )
    }
}

@Composable
private fun FilledArrowCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface,
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        content()
    }
}

@Composable
private fun FeatureBlock(title: String, summary: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = summary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun PreferenceIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(
                color = Color(0x144B7EFF),
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color(0xFF4B7EFF),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun GuideExpandableItem(
    guide: AppGuide,
    initiallyExpanded: Boolean
) {
    var expanded by remember(guide.name) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "guideExpandArrow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = guide.name,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = "▼",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.rotate(rotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            FormattedText(
                text = guide.guide,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14f,
                modifier = Modifier
                    .padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun RuleGuideDialog(
    onDismiss: () -> Unit,
    onOpenParserRules: () -> Unit
) {
    val faqGuideText = LocalResources.current.getText(R.string.faq_a_add_rule)
    MiuixBlurDialog(
        title = stringResource(R.string.oobe_add_rule_guide_title),
        show = true,
        onDismissRequest = onDismiss,
        renderInRootScaffold = false
    ) {
        HtmlFormattedText(
            text = faqGuideText,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = stringResource(R.string.dialog_btn_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(
                    textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                text = stringResource(R.string.oobe_i_understand),
                onClick = onOpenParserRules,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
private fun HtmlFormattedText(
    text: CharSequence,
    modifier: Modifier = Modifier
) {
    val linkColor = MiuixTheme.colorScheme.primary.toArgb()
    val textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                textSize = 14f
                movementMethod = LinkMovementMethod.getInstance()
                setLinkTextColor(linkColor)
            }
        },
        update = { textView ->
            textView.text = text
            textView.setTextColor(textColor)
            textView.textSize = 14f
            textView.setLinkTextColor(linkColor)
        }
    )
}

@Composable
private fun pageBackgroundColor(): Color = MiuixTheme.colorScheme.background

@Composable
private fun permissionSummaryEnhanced(granted: Boolean, text: String, isRequired: Boolean): String {
    val status = if (granted) {
        stringResource(R.string.oobe_btn_granted)
    } else if (isRequired) {
        "❌ ${stringResource(R.string.oobe_btn_grant)}"
    } else {
        stringResource(R.string.oobe_btn_grant)
    }
    return "$status · $text"
}

@Composable
private fun PermissionGrantedCheckmark() {
    androidx.compose.material3.Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = stringResource(R.string.oobe_btn_granted),
        tint = androidx.compose.ui.graphics.Color(0xFF4CAF50),
        modifier = Modifier
            .size(22.dp)
            .padding(end = 4.dp)
    )
}

private fun appIconBitmap(context: Context): Bitmap {
    val drawable = context.packageManager.getApplicationIcon(context.packageName)
    return drawableToBitmap(drawable)
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 192
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 192
    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

private data class AppGuide(
    val name: String,
    val guide: String
)

private enum class SystemStatus {
    FullSupport,
    CommunityVerified,
    UntestedA16,
    SuperIslandOnly,
    Unsupported
}

private fun checkSystemStatus(): SystemStatus {
    val sdkInt = Build.VERSION.SDK_INT
    val romType = RomUtils.getRomType()

    if (RomUtils.isLiveUpdateSupported() && RomUtils.isHyperOs()) {
        return SystemStatus.FullSupport
    }

    if (sdkInt >= 36) {
        return when (romType) {
            "ColorOS", "OneUI", "AOSP" -> SystemStatus.CommunityVerified
            else -> SystemStatus.UntestedA16
        }
    }

    if (RomUtils.isHyperOs() && RomUtils.isHyperOsVersionAtLeast(3, 0, 0)) {
        return SystemStatus.SuperIslandOnly
    }

    return SystemStatus.Unsupported
}

private fun checkNotificationListener(context: Context): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

private fun checkPostNotification(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun checkBatteryOptimization(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/** Read parser_rules_json from a backup file (ZIP or legacy JSON) for conflict resolution. */
private fun readParserJsonFromImportUri(context: Context, uri: Uri): String {
    return try {
        val header = ByteArray(4)
        val isZip = context.contentResolver.openInputStream(uri)?.use { input ->
            if (input.read(header) == 4) {
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            } else false
        } ?: false

        val text = if (isZip) {
            val tempDir = java.io.File(context.cacheDir, "oobe_parser_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.util.zip.ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (entry.name == "settings.json" && !entry.isDirectory) {
                                val targetFile = java.io.File(tempDir, "settings.json")
                                targetFile.outputStream().use { fileOut -> zip.copyTo(fileOut) }
                                break
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
                java.io.File(tempDir, "settings.json").takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: ""
            } finally {
                tempDir.deleteRecursively()
            }
        } else {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
        }

        if (text.isEmpty()) return "[]"
        val root = org.json.JSONObject(text)
        val schemaVersion = root.optInt("schema_version", 1)
        val rawValue = if (schemaVersion >= 2 && root.has("categories")) {
            val catObj = root.optJSONObject("categories")
            val parserBlock = catObj?.optJSONObject("parser_rules")
            parserBlock?.optJSONArray("parsers")
                ?: parserBlock?.optJSONObject("preferences")?.opt("parser_rules_json")
        } else {
            val prefsJson = root.optJSONObject("preferences") ?: root
            prefsJson.opt("parser_rules_json")
        }
        SettingsBackupManager.parserRulesJsonFromBackupValue(rawValue) ?: "[]"
    } catch (_: Exception) { "[]" }
}

private const val OOBE_LAST_STEP = 5


