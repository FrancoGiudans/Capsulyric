package com.example.islandlyrics.feature.customsettings

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurBottomSheet
import com.example.islandlyrics.ui.overlay.superisland.calibration.*
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandTextLimitConfig
import java.util.Locale
import java.util.UUID
import kotlin.math.round
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Both UI stacks edit the same local profile and use the running native island for preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RightCalibrationSheet(miuix: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val owner = remember { Any() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val store = remember { RightCalibrationRuntime.initialize(context) }
    val appPrefs = remember { AppPreferences.of(context) }
    var revision by remember { mutableIntStateOf(0) }
    val ring = LabFeatureManager.isSuperIslandRightIslandProgressLabEnabled(context) &&
        AppPreferences.isSuperIslandCollapsedRightProgressRingEnabled(appPrefs)
    val environment = remember(configuration) { RightCalibrationStore.environment(context) }
    val selected = remember(revision, ring) { store.selected(ring) }
    val defaultCapacity = SuperIslandTextLimitConfig.rightChars(appPrefs,
        appPrefs.getBoolean("lab_super_island_relaxed_text_limits_enabled", false))
    var id by remember { mutableStateOf(selected?.id ?: UUID.randomUUID().toString()) }
    var name by remember { mutableStateOf(selected?.name.orEmpty()) }
    var draft by remember { mutableStateOf(selected?.calibration ?: RightTextCalibration(capacity = defaultCapacity)) }
    var loadedEnvironment by remember { mutableStateOf(selected?.environment ?: environment) }
    var sample by remember { mutableStateOf("一二三四五六七八九十") }
    var raw by remember { mutableStateOf(true) }
    var previewing by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }
    var glyph by remember { mutableStateOf("") }
    var glyphWidth by remember { mutableFloatStateOf(1f) }
    val needsPlayback = stringResource(R.string.right_calibration_needs_playback)
    val savedMessage = stringResource(R.string.right_calibration_saved)

    fun stopPreview() {
        RightCalibrationRuntime.stop(owner)
        previewing = false
    }
    fun preview(): Boolean = RightCalibrationRuntime.show(RightCalibrationRuntime.Preview(
        owner, draft, sample.ifBlank { "♪" }, raw, ring, environment
    ))

    DisposableEffect(lifecycle, store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        store.register(listener)
        val runtimeListener: () -> Unit = {
            if (RightCalibrationRuntime.preview?.owner != owner) previewing = false
        }
        RightCalibrationRuntime.addPreviewListener(runtimeListener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stopPreview()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            store.unregister(listener)
            RightCalibrationRuntime.removePreviewListener(runtimeListener)
            RightCalibrationRuntime.stop(owner)
        }
    }
    LaunchedEffect(draft, sample, raw, environment, ring) {
        kotlinx.coroutines.delay(150)
        if (previewing && !preview()) previewing = false
    }

    val content: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CalibrationText(miuix, stringResource(R.string.right_calibration_instructions))
            CalibrationText(miuix, stringResource(if (ring) R.string.right_calibration_ring else R.string.right_calibration_plain))
            if (loadedEnvironment != environment) {
                CalibrationText(miuix, stringResource(R.string.right_calibration_environment_changed))
            }
            val profiles = remember(revision, ring) { store.profiles().filter { it.ring == ring } }
            if (profiles.isNotEmpty()) {
                CalibrationButton(miuix, stringResource(R.string.right_calibration_profiles)) { showProfiles = !showProfiles }
                if (showProfiles) profiles.forEach { profile ->
                    CalibrationButton(miuix, (if (profile.id == selected?.id) "✓ " else "") + profile.name) {
                        stopPreview()
                        id = profile.id; name = profile.name; draft = profile.calibration
                        loadedEnvironment = profile.environment
                    }
                }
            }
            CalibrationSampleControls(miuix, sample, { sample = it }, raw, { raw = it }, previewing) {
                if (previewing) stopPreview() else {
                    previewing = preview()
                    if (!previewing) Toast.makeText(context, needsPlayback, Toast.LENGTH_LONG).show()
                }
            }
            CalibrationField(miuix, stringResource(R.string.right_calibration_name), name) { name = Glyphs.limit(it, 60) }
            CalibrationSlider(miuix, stringResource(R.string.right_calibration_capacity), draft.capacity, 1f..30f) {
                draft = draft.copy(capacity = it, margin = draft.margin.coerceAtMost(it - 0.1f))
            }
            CalibrationSlider(miuix, stringResource(R.string.right_calibration_margin), draft.margin,
                0f..minOf(3f, draft.capacity - 0.1f)) { draft = draft.copy(margin = it) }

            CalibrationButton(miuix, stringResource(R.string.right_calibration_advanced)) { advanced = !advanced }
            if (advanced) {
                CalibrationSwitch(miuix, stringResource(R.string.right_calibration_letter_mono), draft.letterMonospace) {
                    draft = draft.copy(letterMonospace = it)
                }
                CalibrationSlider(miuix, stringResource(if (draft.letterMonospace) R.string.right_calibration_letter_width
                    else R.string.right_calibration_letter_scale),
                    if (draft.letterMonospace) draft.letterWidth else draft.letterScale,
                    if (draft.letterMonospace) 0.05f..3f else 0.1f..4f) {
                    draft = if (draft.letterMonospace) draft.copy(letterWidth = it) else draft.copy(letterScale = it)
                }
                CalibrationSwitch(miuix, stringResource(R.string.right_calibration_digit_mono), draft.digitMonospace) {
                    draft = draft.copy(digitMonospace = it)
                }
                CalibrationSlider(miuix, stringResource(if (draft.digitMonospace) R.string.right_calibration_digit_width
                    else R.string.right_calibration_digit_scale),
                    if (draft.digitMonospace) draft.digitWidth else draft.digitScale,
                    if (draft.digitMonospace) 0.05f..3f else 0.1f..4f) {
                    draft = if (draft.digitMonospace) draft.copy(digitWidth = it) else draft.copy(digitScale = it)
                }
                CalibrationSlider(miuix, stringResource(R.string.right_calibration_space), draft.spaceWidth, 0.05f..3f) {
                    draft = draft.copy(spaceWidth = it)
                }
                CalibrationSlider(miuix, stringResource(R.string.right_calibration_full_space), draft.fullSpaceWidth, 0.05f..3f) {
                    draft = draft.copy(fullSpaceWidth = it)
                }
                CalibrationSlider(miuix, stringResource(R.string.right_calibration_punctuation), draft.punctuationScale, 0.1f..4f) {
                    draft = draft.copy(punctuationScale = it)
                }
                CalibrationText(miuix, stringResource(R.string.right_calibration_estimate_note))
                CalibrationText(miuix, stringResource(R.string.right_calibration_glyph_note))
                CalibrationField(miuix, stringResource(R.string.right_calibration_glyph), glyph) { glyph = Glyphs.limit(it, 32) }
                CalibrationSlider(miuix, stringResource(R.string.right_calibration_glyph_width), glyphWidth, 0.05f..3f) { glyphWidth = it }
                CalibrationButton(miuix, stringResource(R.string.right_calibration_add_glyph),
                    enabled = Glyphs.parts(glyph).size == 1 && draft.overrides.size < 128) {
                    draft = draft.copy(overrides = draft.overrides + (glyph to glyphWidth))
                }
                draft.overrides.forEach { (character, width) ->
                    CalibrationButton(miuix, "$character = ${format(width)} ×") {
                        draft = draft.copy(overrides = draft.overrides - character)
                    }
                }
            }
            CalibrationButton(miuix, stringResource(R.string.parser_save), enabled = name.isNotBlank()) {
                stopPreview()
                store.save(RightCalibrationProfile(id, name.trim(), environment, ring, draft.sanitized()))
                loadedEnvironment = environment
                Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
            }
            CalibrationButton(miuix, stringResource(R.string.right_calibration_new)) {
                stopPreview(); id = UUID.randomUUID().toString(); name = ""
                loadedEnvironment = environment; draft = RightTextCalibration(capacity = defaultCapacity)
            }
            CalibrationButton(miuix, stringResource(R.string.right_calibration_disable)) {
                stopPreview(); store.disable(ring)
            }
            profiles.firstOrNull { it.id == id }?.let { profile ->
                CalibrationButton(miuix, stringResource(R.string.parser_delete)) {
                    stopPreview(); store.delete(profile)
                    id = UUID.randomUUID().toString(); name = ""
                    loadedEnvironment = environment; draft = RightTextCalibration(capacity = defaultCapacity)
                }
            }
            CalibrationButton(miuix, stringResource(R.string.update_close)) { stopPreview(); onDismiss() }
        }
    }
    if (miuix) {
        MiuixBlurBottomSheet(show = true, title = stringResource(R.string.right_calibration_title),
            onDismissRequest = { stopPreview(); onDismiss() }, content = content)
    } else {
        ModalBottomSheet(onDismissRequest = { stopPreview(); onDismiss() }) {
            CalibrationText(false, stringResource(R.string.right_calibration_title))
            content()
        }
    }
}

@Composable
private fun CalibrationSampleControls(
    miuix: Boolean, sample: String, onSample: (String) -> Unit,
    raw: Boolean, onRaw: (Boolean) -> Unit, previewing: Boolean, onToggle: () -> Unit
) {
    CalibrationText(miuix, stringResource(R.string.right_calibration_samples))
    val samples = listOf(
        "一二三四五六七八九十", "きっと甘えてたの", "iiiiiiiiiiiiiiiiiiii", "WWWWWWWWWW",
        "11111111111111111111", "88888888888888888888", "A A  A   A", "一\u3000二\u3000三\u3000四",
        "，。！？、；：,.!?;:", "一A 1，二W 8。三i!", "ya Ayayayayayayaya"
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        samples.forEach { text -> CalibrationButton(miuix, text.take(8)) { onSample(text) } }
    }
    CalibrationField(miuix, stringResource(R.string.right_calibration_custom_sample), sample) { onSample(Glyphs.limit(it, 160)) }
    CalibrationSwitch(miuix, stringResource(R.string.right_calibration_raw), raw, onRaw)
    CalibrationButton(miuix, stringResource(if (previewing) R.string.right_calibration_stop else R.string.right_calibration_start),
        onClick = onToggle)
}

private fun format(value: Float) = String.format(Locale.getDefault(), "%.2f", value)

@Composable
private fun CalibrationText(miuix: Boolean, text: String) {
    if (miuix) top.yukonga.miuix.kmp.basic.Text(text, style = MiuixTheme.textStyles.main,
        color = MiuixTheme.colorScheme.onSurface)
    else androidx.compose.material3.Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun CalibrationField(miuix: Boolean, label: String, value: String, onChange: (String) -> Unit) {
    if (miuix) top.yukonga.miuix.kmp.basic.TextField(value = value, onValueChange = onChange,
        label = label, modifier = Modifier.fillMaxWidth(), singleLine = true)
    else androidx.compose.material3.OutlinedTextField(value = value, onValueChange = onChange,
        label = { androidx.compose.material3.Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun CalibrationSlider(miuix: Boolean, label: String, value: Float,
    range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        CalibrationText(miuix, "$label: ${format(value)}")
        val change: (Float) -> Unit = { onChange((round(it * 20f) / 20f).coerceIn(range)) }
        if (miuix) top.yukonga.miuix.kmp.basic.Slider(value = value.coerceIn(range), valueRange = range, onValueChange = change)
        else androidx.compose.material3.Slider(value = value.coerceIn(range), valueRange = range, onValueChange = change)
    }
}

@Composable
private fun CalibrationSwitch(miuix: Boolean, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    if (miuix) top.yukonga.miuix.kmp.preference.SwitchPreference(title = label, checked = checked, onCheckedChange = onChange)
    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        androidx.compose.material3.Text(label, Modifier.weight(1f).padding(end = 12.dp))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CalibrationButton(miuix: Boolean, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    if (miuix) top.yukonga.miuix.kmp.basic.TextButton(text = label, onClick = onClick, enabled = enabled)
    else androidx.compose.material3.TextButton(onClick = onClick, enabled = enabled) { CalibrationText(false, label) }
}
