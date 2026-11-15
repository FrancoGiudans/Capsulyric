package com.example.islandlyrics.feature.customsettings

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.islandlyrics.R
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.runtime.service.LyricService
import com.example.islandlyrics.feature.customsettings.miuix.MiuixRightCalibrationContent
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurBottomSheet
import com.example.islandlyrics.ui.overlay.superisland.calibration.*
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandTextLimitConfig
import java.text.DateFormat
import java.util.Date
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Preparation and results only. The service owns all background calibration work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RightCalibrationSheet(miuix: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { RightCalibrationRuntime.initialize(context) }
    val checkpoints = remember { RightWizardCheckpointStore(context) }
    val state by RightWizardRuntime.state.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    var starting by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val environment = RightCalibrationStore.environment(context)
    val ring = remember(revision) { RightWizardController.currentRing(context) }
    val draft = remember(revision, ring) { checkpoints.read(ring) }
    val profiles = remember(revision, ring) { store.profiles().filter { it.ring == ring } }
    val compatible = draft?.profile?.matches(environment, ring) == true
    val completed = remember(draft, state.running) { !state.running && draft?.restore()?.completed == true }

    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        store.register(listener)
        val preferences = AppPreferences.of(context)
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.unregister(listener); preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun launchService() {
        try {
            RightWizardRuntime.publish(RightWizardUiState())
            ContextCompat.startForegroundService(context,
                Intent(context, LyricService::class.java).setAction(RightWizardController.ACTION_START)
                    .putExtra(RightWizardController.EXTRA_STARTED_FOR_CALIBRATION, !LyricService.isAlive))
        } catch (_: Exception) {
            starting = false
            message = context.getString(R.string.wizard_start_failed)
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (starting && Settings.canDrawOverlays(context)) launchService()
        else { starting = false; message = context.getString(R.string.wizard_overlay_required) }
    }
    fun requestOverlayOrStart() {
        if (Settings.canDrawOverlays(context)) launchService() else {
            runCatching { overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"))) }.onFailure {
                starting = false; message = context.getString(R.string.wizard_overlay_required)
            }
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && starting) requestOverlayOrStart()
        else { starting = false; message = context.getString(R.string.wizard_notifications_required) }
    }

    fun start(resume: Boolean, more: Boolean = false) {
        message = ""
        if (!RomUtils.isHyperOs() && !RomUtils.isIslandSupported()) {
            message = context.getString(R.string.wizard_unsupported); return
        }
        val checkpoint = if (resume) {
            draft?.takeIf { compatible }?.let { if (more) it.copy(detailed = true) else it }
        } else {
            val p = AppPreferences.of(context)
            val selected = store.selected(ring)?.takeIf { it.matches(environment, ring) }
            val seed = selected?.calibration ?: RightTextCalibration(capacity = SuperIslandTextLimitConfig.rightChars(p,
                p.getBoolean("lab_super_island_relaxed_text_limits_enabled", false)))
            RightWizardCheckpoint(RightCalibrationProfile(name = context.getString(R.string.wizard_profile_name,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())) + " · " +
                context.getString(if (ring) R.string.wizard_ring_on else R.string.wizard_ring_off),
                environment = environment, ring = ring, calibration = seed), customSample = custom)
        }
        if (checkpoint == null) { message = context.getString(R.string.wizard_environment_changed); return }
        if (!checkpoints.write(checkpoint)) { message = context.getString(R.string.wizard_storage_failed); return }
        starting = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            starting = false; message = context.getString(R.string.wizard_notifications_required)
        } else requestOverlayOrStart()
    }

    LaunchedEffect(state.running, state.readyToBackground, state.message) {
        if (starting && state.running && state.readyToBackground) {
            starting = false
            runCatching { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
                .onFailure {
                    context.startService(Intent(context, LyricService::class.java).setAction(RightWizardController.ACTION_STOP))
                    message = context.getString(R.string.wizard_start_failed)
                }
        } else if (starting && !state.running && state.message.isNotEmpty()) {
            starting = false
            message = state.message
        }
    }

    fun dismiss() {
        if (state.running) context.startService(Intent(context, LyricService::class.java).setAction(RightWizardController.ACTION_STOP))
        onDismiss()
    }

    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WizardText( stringResource(if (ring) R.string.wizard_ring_on else R.string.wizard_ring_off))
            WizardText( stringResource(R.string.wizard_ring_scope))
            WizardText( stringResource(R.string.wizard_intro))
            if (message.isNotEmpty()) WizardText( message)
            else if (state.message.isNotEmpty()) WizardText( state.message)
            if (draft != null) {
                if (completed) {
                    WizardText(stringResource(R.string.wizard_completed_title, draft.answers.size))
                    WizardText(stringResource(R.string.wizard_completed_draft))
                } else WizardText(stringResource(R.string.wizard_checkpoint, draft.answers.size))
                if (!compatible) WizardText( stringResource(R.string.wizard_environment_changed))
                WizardButton( stringResource(if (completed) R.string.wizard_view_result else R.string.wizard_resume),
                    !starting && !state.running && compatible) { start(resume = true) }
                if (completed && !draft.detailed) WizardButton( stringResource(R.string.wizard_more),
                    !starting && !state.running && compatible) { start(resume = true, more = true) }
            }
            WizardField( stringResource(R.string.wizard_extra_text), custom) { custom = Glyphs.limit(it, 160) }
            WizardButton( stringResource(if (draft == null) R.string.wizard_begin else R.string.wizard_restart),
                !starting && !state.running) { start(resume = false) }
            if (starting) WizardText( stringResource(R.string.wizard_preparing))
            if (profiles.isNotEmpty()) {
                WizardText( stringResource(R.string.wizard_saved_profiles))
                profiles.forEach { profile ->
                    WizardButton( (if (store.selected(ring)?.id == profile.id) "✓ " else "") + profile.name,
                        !starting && !state.running && profile.matches(environment, ring)) { store.save(profile) }
                }
            }
            WizardText( stringResource(R.string.wizard_font_note))
            WizardButton( stringResource(R.string.wizard_defaults), !starting && !state.running) { store.disable(ring) }
            WizardButton( stringResource(R.string.update_close), onClick = ::dismiss)
        }
    }
    if (miuix) MiuixBlurBottomSheet(show = true, title = stringResource(R.string.right_calibration_title),
        startAction = { IconButton(onClick = ::dismiss) {
            Icon(MiuixIcons.Close, stringResource(R.string.update_close), tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
        } }, onDismissRequest = ::dismiss) {
        MiuixRightCalibrationContent(ring = ring,
            otherModeSaved = store.selected(!ring)?.matches(environment, !ring) == true,
            environment = environment, profiles = profiles,
            selectedId = store.selected(ring)?.takeIf { it.matches(environment, ring) }?.id,
            draftCount = draft?.answers?.size, compatible = compatible, completed = completed,
            detailed = draft?.detailed == true, enabled = !starting && !state.running,
            starting = starting, message = message.ifEmpty { state.message }, custom = custom,
            onCustomChange = { custom = Glyphs.limit(it, 160) }, onStart = { start(false) },
            onResume = { start(true) }, onMore = { start(true, true) }, onSelect = { store.save(it) },
            onDefaults = { store.disable(ring) })
    }
    else ModalBottomSheet(onDismissRequest = ::dismiss) { content() }
}

@Composable
private fun WizardText(text: String) { androidx.compose.material3.Text(text) }

@Composable
private fun WizardButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(enabled = enabled, onClick = onClick) { androidx.compose.material3.Text(label) }
}

@Composable
private fun WizardField(label: String, value: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(value = value, onValueChange = onChange,
        label = { androidx.compose.material3.Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}
