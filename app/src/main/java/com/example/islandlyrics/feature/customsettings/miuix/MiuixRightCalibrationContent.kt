package com.example.islandlyrics.feature.customsettings.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.ui.overlay.superisland.calibration.RightCalibrationProfile
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MiuixRightCalibrationContent(
    ring: Boolean,
    otherModeSaved: Boolean,
    environment: String,
    profiles: List<RightCalibrationProfile>,
    selectedId: String?,
    draftCount: Int?,
    compatible: Boolean,
    completed: Boolean,
    detailed: Boolean,
    enabled: Boolean,
    starting: Boolean,
    message: String,
    custom: String,
    onCustomChange: (String) -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onMore: () -> Unit,
    onSelect: (RightCalibrationProfile) -> Unit,
    onDefaults: () -> Unit
) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card {
                BasicComponent(title = stringResource(if (ring) R.string.wizard_ring_on else R.string.wizard_ring_off),
                    summary = stringResource(R.string.wizard_ring_scope))
                if (otherModeSaved) BasicComponent(title = stringResource(R.string.wizard_other_mode_saved),
                    summary = stringResource(R.string.wizard_other_mode_hint))
            }
            Text(stringResource(R.string.wizard_intro), style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (draftCount != null) Card {
                BasicComponent(title = stringResource(if (completed) R.string.wizard_view_result else R.string.wizard_resume),
                    summary = stringResource(if (completed) R.string.wizard_completed_draft else R.string.wizard_checkpoint, draftCount))
                if (!compatible) BasicComponent(summary = stringResource(R.string.wizard_environment_changed))
                if (completed && !detailed) BasicComponent(title = stringResource(R.string.wizard_more),
                    enabled = enabled && compatible, onClick = onMore)
            }
            Card {
                BasicComponent(title = stringResource(R.string.wizard_extra_text),
                    summary = stringResource(R.string.wizard_extra_hint), enabled = enabled,
                    onClick = { showCustom = !showCustom })
                if (showCustom) TextField(value = custom, onValueChange = onCustomChange,
                    label = stringResource(R.string.wizard_extra_text), singleLine = true, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp))
            }
            if (profiles.isNotEmpty()) {
                Text(stringResource(R.string.wizard_saved_profiles), style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Card(insideMargin = PaddingValues(0.dp)) {
                    RadioButtonPreference(title = stringResource(R.string.wizard_defaults), selected = selectedId == null,
                        enabled = enabled, onClick = onDefaults)
                    profiles.forEach { profile ->
                        val matches = profile.matches(environment, ring)
                        RadioButtonPreference(title = profile.name, selected = selectedId == profile.id,
                            summary = if (!matches) stringResource(R.string.wizard_environment_changed) else null,
                            enabled = enabled && matches, onClick = { onSelect(profile) })
                    }
                }
            }
            Text(stringResource(R.string.wizard_font_note), style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (message.isNotEmpty() || starting) Text(if (starting) stringResource(R.string.wizard_preparing) else message,
            style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.primary)
        val resume = draftCount != null && compatible
        Button(onClick = if (resume) onResume else onStart, enabled = enabled,
            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColorsPrimary()) {
            Text(stringResource(if (resume) {
                if (completed) R.string.wizard_view_result else R.string.wizard_resume
            } else R.string.wizard_begin))
        }
        if (draftCount != null) TextButton(text = stringResource(R.string.wizard_restart),
            onClick = onStart, enabled = enabled, modifier = Modifier.fillMaxWidth())
    }
}
