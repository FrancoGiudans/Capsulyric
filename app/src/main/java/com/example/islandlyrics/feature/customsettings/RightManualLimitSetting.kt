package com.example.islandlyrics.feature.customsettings

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.ui.material.blur.MaterialBlurAlertDialog
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurDialog
import com.example.islandlyrics.ui.overlay.superisland.calibration.*

@Composable
internal fun RightManualLimitSetting(
    miuix: Boolean,
    value: Float,
    onValueChange: (Float) -> Unit,
    content: @Composable (onChange: (Float) -> Unit, summary: String?) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { RightCalibrationRuntime.initialize(context) }
    var revision by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<RightManualLimitChange?>(null) }
    val configuration = LocalConfiguration.current
    val ring = remember(revision) { RightWizardController.currentRing(context) }
    val environment = remember(revision, configuration) { RightCalibrationStore.environment(context) }
    val active = remember(revision, ring, environment) { store.active(environment, ring) }
    DisposableEffect(store, context) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        val preferences = AppPreferences.of(context)
        store.register(listener)
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.unregister(listener); preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(active) { if (pending?.profile != active) pending = null }
    content(onChange@{ requestedValue ->
        if (requestedValue == value) return@onChange
        val currentRing = RightWizardController.currentRing(context)
        val profile = store.active(RightCalibrationStore.environment(context), currentRing)
        if (profile == null) onValueChange(requestedValue)
        else if (pending == null) pending = RightManualLimitChange(profile, requestedValue)
    }, if (active != null) stringResource(R.string.wizard_using_calibration) else null)
    if (pending != null) RightManualModeDialog(miuix, onDismiss = { pending = null }, onConfirm = {
        val change = pending
        pending = null
        if (change != null && store.confirmManualLimit(change, RightCalibrationStore.environment(context),
                RightWizardController.currentRing(context))) change.value?.let(onValueChange)
    })
}

@Composable
internal fun RightManualModeDialog(miuix: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (miuix) MiuixBlurDialog(show = true, title = stringResource(R.string.wizard_manual_title),
        summary = stringResource(R.string.wizard_manual_warning), onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            top.yukonga.miuix.kmp.basic.TextButton(text = stringResource(R.string.dialog_btn_cancel),
                onClick = onDismiss, modifier = Modifier.weight(1f))
            top.yukonga.miuix.kmp.basic.TextButton(text = stringResource(R.string.wizard_manual_confirm),
                onClick = onConfirm, modifier = Modifier.weight(1f),
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary())
        }
    } else MaterialBlurAlertDialog(onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(stringResource(R.string.wizard_manual_title)) },
        text = { androidx.compose.material3.Text(stringResource(R.string.wizard_manual_warning)) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) {
            androidx.compose.material3.Text(stringResource(R.string.wizard_manual_confirm))
        } }, dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) {
            androidx.compose.material3.Text(stringResource(R.string.dialog_btn_cancel))
        } })
}
