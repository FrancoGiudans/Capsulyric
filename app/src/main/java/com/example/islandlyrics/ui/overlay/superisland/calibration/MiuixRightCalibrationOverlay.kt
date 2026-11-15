package com.example.islandlyrics.ui.overlay.superisland.calibration

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.*
import com.example.islandlyrics.R
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A compact control surface, hosted by the service rather than a foreground Activity. */
internal class MiuixRightCalibrationOverlay(
    private val context: Context,
    private val answer: (String, Boolean) -> Unit,
    private val missing: () -> Unit,
    private val replay: () -> Unit,
    private val back: () -> Unit,
    private val pause: () -> Unit,
    private val discard: () -> Unit,
    private val save: () -> Unit,
    private val more: () -> Unit
) : RightCalibrationControlWindow, LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    private val manager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val params = WindowManager.LayoutParams(
        minOf(context.resources.displayMetrics.widthPixels - dp(24), dp(400)),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(32) }
    private val root = ComposeView(context)
    private var attached = false
    private data class Presentation(val probe: CalibrationProbe, val token: String, val count: Int,
        val detailed: Boolean, val ring: Boolean, val summary: String?)
    private var presentation by mutableStateOf<Presentation?>(null)
    private var feedbackEnabled by mutableStateOf(false)
    private var missingMessage by mutableStateOf(false)
    private var actionsExpanded by mutableStateOf(false)

    override fun attach() {
        saved.performAttach()
        saved.performRestore(null)
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        root.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        registry.currentState = Lifecycle.State.CREATED
        root.setContent { MiuixAppTheme { Controls() } }
        manager.addView(root, params)
        attached = true
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun close() {
        if (attached) runCatching { manager.removeView(root) }
        attached = false
        registry.currentState = Lifecycle.State.DESTROYED
        root.disposeComposition()
        viewModelStore.clear()
    }

    override fun render(probe: CalibrationProbe, presentationToken: String, count: Int, detailed: Boolean,
        ring: Boolean, summary: String?) {
        feedbackEnabled = false
        missingMessage = false
        actionsExpanded = false
        presentation = Presentation(probe, presentationToken, count, detailed, ring, summary)
    }

    override fun setAnswering(enabled: Boolean) { feedbackEnabled = enabled }
    override fun showMissing() { missingMessage = true }

    @Composable
    private fun Controls() {
        val p = presentation ?: return
        val maxHeight = minOf(520, (context.resources.configuration.screenHeightDp * 0.7f).toInt()).dp
        Card(Modifier.fillMaxWidth(), cornerRadius = 28.dp,
            insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
                Box(Modifier.fillMaxWidth().height(20.dp).moveHandle(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp, 4.dp).background(
                        MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
                }
                BasicComponent(
                    title = stringResource(RightCalibrationOverlay.stageTitle(p.probe.stage)),
                    summary = stringResource(if (p.ring) R.string.wizard_ring_on else R.string.wizard_ring_off),
                    insideMargin = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.moveHandle(),
                    endActions = {
                        IconButton(onClick = { actionsExpanded = !actionsExpanded }) {
                            Icon(MiuixIcons.More, stringResource(R.string.wizard_more_actions),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
                        }
                        IconButton(onClick = pause) {
                            Icon(MiuixIcons.Close, stringResource(R.string.wizard_pause),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
                        }
                    }
                )
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(when {
                        missingMessage -> stringResource(R.string.wizard_missing_help)
                        p.summary != null -> p.summary
                        p.probe.blocked -> stringResource(R.string.wizard_blocked)
                        p.probe.scrolling -> stringResource(R.string.wizard_scroll_instruction)
                        else -> stringResource(R.string.wizard_judge_instruction, p.count + 1)
                    }, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    if (p.summary == null) Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryVariant)) {
                        Text(p.probe.sample, style = MiuixTheme.textStyles.main, maxLines = 3,
                            overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.wizard_expected_ending,
                            Glyphs.parts(p.probe.sample).takeLast(8).joinToString("")),
                            style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    if (actionsExpanded) Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryVariant)) {
                        if (p.summary == null) {
                            BasicComponent(title = stringResource(R.string.wizard_replay), onClick = replay)
                            BasicComponent(title = stringResource(R.string.wizard_back), onClick = back, enabled = p.count > 0)
                        }
                        BasicComponent(title = stringResource(R.string.wizard_discard), onClick = discard)
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (p.summary == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button({ answer(p.token, false) }, Modifier.weight(1f), enabled = feedbackEnabled,
                            minHeight = 48.dp, cornerRadius = 24.dp) {
                            Text(stringResource(R.string.wizard_incomplete))
                        }
                        Button({ answer(p.token, true) }, Modifier.weight(1f), enabled = feedbackEnabled,
                            minHeight = 48.dp, cornerRadius = 24.dp,
                            colors = ButtonDefaults.buttonColorsPrimary()) { Text(stringResource(R.string.wizard_complete)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(stringResource(R.string.wizard_missing), missing, Modifier.fillMaxWidth())
                } else {
                    Button(save, Modifier.fillMaxWidth(), minHeight = 48.dp, cornerRadius = 24.dp,
                        colors = ButtonDefaults.buttonColorsPrimary()) {
                        Text(stringResource(R.string.wizard_save))
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!p.detailed) TextButton(stringResource(R.string.wizard_more), more, Modifier.fillMaxWidth())
                }
            }
        }
    }

    private fun Modifier.moveHandle() = pointerInput(Unit) {
        detectDragGestures { change, delta ->
            change.consume()
            val metrics = context.resources.displayMetrics
            params.x = (params.x + delta.x).toInt().coerceIn(
                -(metrics.widthPixels - params.width) / 2, (metrics.widthPixels - params.width) / 2)
            params.y = (params.y - delta.y).toInt().coerceIn(dp(16),
                (metrics.heightPixels - root.height - dp(80)).coerceAtLeast(dp(16)))
            if (attached) manager.updateViewLayout(root, params)
        }
    }
}
