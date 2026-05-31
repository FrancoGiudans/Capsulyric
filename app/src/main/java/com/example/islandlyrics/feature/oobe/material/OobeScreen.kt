package com.example.islandlyrics.feature.oobe.material

import android.Manifest
import com.example.islandlyrics.feature.settings.SettingsActivity
import com.example.islandlyrics.feature.customsettings.CustomSettingsActivity
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.islandlyrics.feature.parserrule.ParserRuleActivity
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.faq.material.FormattedText
import com.example.islandlyrics.core.platform.RomUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OobeScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

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

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(pagerState.pageCount) { iteration ->
                        val color = if (pagerState.currentPage == iteration) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Back Button (only if not on first page)
                    if (pagerState.currentPage > 0) {
                        TextButton(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                        ) {
                            Text(text = stringResource(R.string.oobe_back))
                        }
                    }

                    Button(
                        onClick = {
                            if (pagerState.currentPage < pagerState.pageCount - 1) {
                                // Block navigation from permissions step if required perms not granted
                                if (pagerState.currentPage == 1 && (!listenerGranted || !postGranted)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.oobe_perm_required_hint),
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                    return@Button
                                }
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                onFinish()
                            }
                        }
                    ) {
                        Text(
                            text = if (pagerState.currentPage == pagerState.pageCount - 1) 
                                stringResource(R.string.oobe_finish_start) 
                            else 
                                stringResource(R.string.oobe_next)
                        )
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            when (page) {
                0 -> WelcomeStep()
                1 -> PermissionsStep(
                    listenerGranted = listenerGranted,
                    postGranted = postGranted,
                    onListenerGrantedChange = { listenerGranted = it },
                    onPostGrantedChange = { postGranted = it }
                )
                2 -> AppSetupStep()
                3 -> CompletionStep(onFinish)
            }
        }
    }
}

@Composable
fun WelcomeStep() {
    val systemStatus = remember { checkSystemStatus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_music_note), // Assuming this exists
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.oobe_welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.oobe_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        
        // System Compatibility Warning
        if (systemStatus != SystemStatus.FullSupport) {
            val (color, textRes) = when (systemStatus) {
                SystemStatus.CommunityVerified -> MaterialTheme.colorScheme.tertiaryContainer to R.string.oobe_warning_community_verified
                SystemStatus.UntestedA16 -> MaterialTheme.colorScheme.tertiaryContainer to R.string.oobe_warning_untested_system
                SystemStatus.SuperIslandOnly -> MaterialTheme.colorScheme.tertiaryContainer to R.string.oobe_warning_super_island_limited
                SystemStatus.Unsupported -> MaterialTheme.colorScheme.errorContainer to R.string.oobe_error_unsupported_device
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = color),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(textRes),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = stringResource(R.string.oobe_privacy_notice),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start
            )
        }
    }
}

enum class SystemStatus {
    FullSupport,
    CommunityVerified,
    UntestedA16,
    SuperIslandOnly,
    Unsupported
}

private fun checkSystemStatus(): SystemStatus {
    val sdkInt = android.os.Build.VERSION.SDK_INT
    val romType = RomUtils.getRomType()

    // Tier 1: Full Support (HyperOS 3.0.300+ on A16+)
    if (RomUtils.isLiveUpdateSupported() && RomUtils.isHyperOs()) {
        return SystemStatus.FullSupport
    }

    // Tier 2 & 3: Android 16+ non-verified or community-verified
    if (sdkInt >= 36) {
        return when (romType) {
            "ColorOS", "OneUI", "AOSP" -> SystemStatus.CommunityVerified
            else -> SystemStatus.UntestedA16
        }
    }

    // Tier 4: Super Island Only (HyperOS 3.0.x below 3.0.300)
    if (RomUtils.isHyperOs() && RomUtils.isHyperOsVersionAtLeast(3, 0, 0)) {
        return SystemStatus.SuperIslandOnly
    }

    // Tier 5: Unsupported
    return SystemStatus.Unsupported
}

@Composable
fun PermissionsStep(
    listenerGranted: Boolean,
    postGranted: Boolean,
    onListenerGrantedChange: (Boolean) -> Unit,
    onPostGrantedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    // Battery & Autostart states remain local
    var batteryGranted by remember { mutableStateOf(checkBatteryOptimization(context)) }
    val autostartIntent = remember { RomUtils.getAutostartPermissionIntent(context) }
    val showAutostart = RomUtils.isHeavySkin() && autostartIntent != null

    // Privacy/scope dialog for notification listener
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = stringResource(R.string.oobe_step_permissions),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Required permissions hint
        Text(
            text = stringResource(R.string.oobe_perm_required_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        PermissionItemEnhanced(
            title = stringResource(R.string.oobe_perm_listener_title),
            desc = stringResource(R.string.oobe_perm_listener_desc),
            granted = listenerGranted,
            isRequired = true,
            onClick = {
                if (!listenerGranted) {
                    showListenerScopeDialog = true
                } else {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(4.dp))
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionItemEnhanced(
                title = stringResource(R.string.oobe_perm_post_title),
                desc = stringResource(R.string.oobe_perm_post_desc),
                granted = postGranted,
                isRequired = true,
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        // Autostart (High Priority for Heavy Skins)
        if (showAutostart) {
            PermissionItemEnhanced(
                title = stringResource(R.string.oobe_perm_autostart_title),
                desc = stringResource(R.string.oobe_perm_autostart_desc),
                granted = false,
                isRequired = false,
                onClick = {
                     try {
                         context.startActivity(autostartIntent)
                     } catch (e: Exception) {
                         // Fallback just in case
                     }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        PermissionItemEnhanced(
            title = stringResource(R.string.oobe_perm_battery_title),
            desc = stringResource(R.string.oobe_perm_battery_desc),
            granted = batteryGranted,
            isRequired = false,
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        )
    }

    // Privacy / Scope Dialog for Notification Listener
    if (showListenerScopeDialog) {
        AlertDialog(
            onDismissRequest = { showListenerScopeDialog = false },
            title = {
                Text(
                    stringResource(R.string.oobe_perm_listener_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.oobe_perm_listener_scope),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showListenerScopeDialog = false
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) {
                    Text(stringResource(R.string.dialog_btn_understand))
                }
            },
            dismissButton = {
                TextButton(onClick = { showListenerScopeDialog = false }) {
                    Text(stringResource(R.string.dialog_btn_cancel))
                }
            }
        )
    }
}

@Composable
fun PermissionItemEnhanced(
    title: String, 
    desc: String, 
    granted: Boolean, 
    isRequired: Boolean,
    onClick: () -> Unit
) {
    val statusColor = if (granted) 
        MaterialTheme.colorScheme.primary 
    else if (isRequired) 
        MaterialTheme.colorScheme.error 
    else 
        MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.SemiBold
                )
                if (isRequired) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "●",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc, 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        // Status badge
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (granted) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (granted)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Text(
                text = if (granted) 
                    stringResource(R.string.oobe_btn_granted) 
                else 
                    stringResource(R.string.oobe_btn_grant),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun AppSetupStep() {
    val context = LocalContext.current
    var showGuideDialog by remember { mutableStateOf(false) }
    
    // Data Preparation
    data class AppGuide(val name: String, val guide: String)
    val guides = listOf(
        AppGuide(stringResource(R.string.oobe_tab_qq), stringResource(R.string.oobe_guide_qq)),
        AppGuide(stringResource(R.string.oobe_tab_netease), stringResource(R.string.oobe_guide_netease)),
        AppGuide(stringResource(R.string.oobe_tab_xiaomi), stringResource(R.string.oobe_guide_xiaomi))
    )


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.oobe_step_apps),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.oobe_apps_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // Expandable List
        guides.forEachIndexed { index, appGuide ->
            SetupGuideItem(
                name = appGuide.name,
                guide = appGuide.guide,
                initiallyExpanded = index == 0 // Expand first item by default
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Online Lyrics Alternative
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.oobe_online_alt_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.oobe_online_alt_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                FormattedText(
                    text = context.resources.getText(R.string.oobe_online_alt_guide),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14f
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(
            onClick = {
                showGuideDialog = true
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(R.string.oobe_app_not_in_list))
        }


        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                context.startActivity(Intent(context, com.example.islandlyrics.feature.faq.FAQActivity::class.java))
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(R.string.faq_title))
        }
    }

    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.oobe_add_rule_guide_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FormattedText(
                        text = context.resources.getText(R.string.faq_a_add_rule),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14f
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGuideDialog = false
                        context.startActivity(Intent(context, ParserRuleActivity::class.java))
                    }
                ) {
                    Text(stringResource(R.string.oobe_i_understand))
                }
            }
        )
    }
}

@Composable
fun SetupGuideItem(name: String, guide: String, initiallyExpanded: Boolean = false) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    // Clean formatting for simplicity
    val cleanText = guide.replace("<b>", "").replace("</b>", "")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            // Scaled chevron
            val rotation = if (expanded) 180f else 0f
            Icon(
                imageVector = OobeIcons.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier
                    .rotate(rotation)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Content
        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = cleanText,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

fun Modifier.rotate(degrees: Float): Modifier = this.then(
    Modifier.graphicsLayer(rotationZ = degrees)
)


@Composable
fun CompletionStep(onFinish: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_check_circle), // Need to ensure this exists or use check
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.oobe_finish_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.oobe_finish_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        // Personalization Button
        OutlinedButton(
            onClick = {
                val intent = Intent(context, com.example.islandlyrics.feature.customsettings.CustomSettingsActivity::class.java)
                context.startActivity(intent)
            }
        ) {
            Text(text = stringResource(R.string.page_title_personalization))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Open Settings Button
        OutlinedButton(
            onClick = {
                val intent = Intent(context, com.example.islandlyrics.feature.settings.SettingsActivity::class.java)
                context.startActivity(intent)
            }
        ) {
            Text(text = stringResource(R.string.oobe_btn_settings))
        }
    }
}

fun checkNotificationListener(context: Context): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

fun checkPostNotification(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    return true
}

fun checkBatteryOptimization(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private object OobeIcons {
    val KeyboardArrowDown: ImageVector
        get() {
            if (_keyboardArrowDown != null) return _keyboardArrowDown!!
            _keyboardArrowDown = ImageVector.Builder(
                name = "KeyboardArrowDown",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(7.41f, 8.59f)
                    lineTo(12f, 13.17f)
                    lineTo(16.59f, 8.59f)
                    lineTo(18f, 10f)
                    lineTo(12f, 16f)
                    lineTo(6f, 10f)
                    close()
                }
            }.build()
            return _keyboardArrowDown!!
        }
    private var _keyboardArrowDown: ImageVector? = null
}
