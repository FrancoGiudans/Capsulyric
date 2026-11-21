package com.example.islandlyrics.ui.overlay.floating

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.ui.overlay.model.SecondaryTextMode
import com.example.islandlyrics.ui.overlay.model.UIState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A Compose root for each independently attached WindowManager surface. */
internal class FloatingLyricsComposeHost(context: Context) : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    val view = ComposeView(context)

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        installOwners(view)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun setContent(content: @Composable () -> Unit) {
        view.setContent { MiuixAppTheme(content = content) }
    }

    fun installOwners(root: View) {
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
    }

    fun close() {
        registry.currentState = Lifecycle.State.DESTROYED
        view.disposeComposition()
        viewModelStore.clear()
    }
}

internal class FloatingLyricsMiuixPanel(
    context: Context,
    private val minimalLyrics: FloatingLyricsContentView,
    private val expandedLyrics: FloatingLyricsContentView,
    private val actions: FloatingLyricsActionController,
    private val expand: () -> Unit,
    private val collapse: () -> Unit,
    private val openSettings: (View) -> Unit,
    private val onBackgroundColorsChanged: (Int, Int) -> Unit
) {
    private val host = FloatingLyricsComposeHost(context)
    val view get() = host.view
    var expanded by mutableStateOf(false)
    var blur by mutableStateOf(false)

    private var title by mutableStateOf("")
    private var artist by mutableStateOf("")
    private var artwork by mutableStateOf<Bitmap?>(null)
    private var showCompactArtwork by mutableStateOf(true)
    private var playing by mutableStateOf(false)
    private var compactBackground by mutableStateOf(FloatingLyricsStyleStore(context).style.enableTextBackground)

    init {
        // The native lyrics surface covers most of the card and needs the same
        // click target as its Compose margin, including for accessibility.
        minimalLyrics.contentDescription = context.getString(R.string.floating_settings_expand)
        minimalLyrics.setOnClickListener { expand() }
        expandedLyrics.setOnClickListener { collapse() }
        host.setContent {
            val colors = MiuixTheme.colorScheme
            SideEffect {
                onBackgroundColorsChanged(colors.background.toArgb(), colors.surfaceContainer.toArgb())
            }
            if (expanded) {
                FloatingLyricsMiuixExpandedPanel(
                    title = title,
                    artist = artist,
                    artwork = artwork,
                    playing = playing,
                    blur = blur,
                    onCollapse = collapse,
                    onOpenSettings = { openSettings(view) },
                    onOpenPlayer = { if (actions.openPlayingApp()) collapse() },
                    onClose = actions::closeFloatingLyrics,
                    onMediaAction = actions::sendMediaAction
                ) { modifier -> Lyrics(expandedLyrics, modifier) }
            } else {
                val expandLabel = stringResource(R.string.floating_settings_expand)
                FloatingLyricsMiuixCompactPanel(
                    artwork = artwork.takeIf { showCompactArtwork },
                    background = compactBackground,
                    onExpand = expand,
                    modifier = Modifier.semantics { contentDescription = expandLabel }
                ) { modifier -> Lyrics(minimalLyrics, modifier) }
            }
        }
    }

    fun bind(state: UIState, style: FloatingLyricsStyle, playing: Boolean) {
        title = state.title
        artist = state.artist
        artwork = state.albumArt
        showCompactArtwork = style.showAlbumArt
        compactBackground = style.enableTextBackground
        this.playing = playing
    }

    fun close() = host.close()

    fun installOwners(root: View) = host.installOwners(root)

    @Composable
    private fun Lyrics(lyrics: FloatingLyricsContentView, modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = {
                (lyrics.parent as? ViewGroup)?.removeView(lyrics)
                lyrics
            }
        )
    }
}

@Composable
internal fun FloatingLyricsMiuixCompactPanel(
    artwork: Bitmap?,
    background: Boolean,
    onExpand: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    lyrics: @Composable (Modifier) -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.defaultColors(
            color = if (background) MiuixTheme.colorScheme.surfaceContainer else Color.Transparent
        ),
        showIndication = true,
        onClick = onExpand
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            artwork?.let { Artwork(it, 32.dp, 8.dp) }
            lyrics(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun FloatingLyricsMiuixExpandedPanel(
    title: String,
    artist: String,
    artwork: Bitmap?,
    playing: Boolean,
    blur: Boolean,
    onCollapse: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    onOpenPlayer: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onMediaAction: ((String) -> Unit)? = null,
    lyrics: @Composable (Modifier) -> Unit
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = FloatingLyricsChrome.ExpandedPanelRadiusDp.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(
            color = floatingPanelColor(blur)
        ),
        showIndication = true,
        onClick = onCollapse
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            artwork?.let { Artwork(it, 40.dp, 10.dp) }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    title.ifBlank { stringResource(R.string.media_control_unknown_title) },
                    style = MiuixTheme.textStyles.main.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurfaceContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    artist.ifBlank { stringResource(R.string.media_control_unknown_artist) },
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FloatingIcon(rememberVectorPainter(MiuixIcons.Settings), R.string.settings_floating_lyrics, onClick = onOpenSettings)
            FloatingIcon(rememberVectorPainter(MiuixIcons.Music), R.string.floating_window_open_player, onClick = onOpenPlayer)
            FloatingIcon(rememberVectorPainter(MiuixIcons.Close), R.string.floating_window_close, onClick = onClose)
        }
        lyrics(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp))
        HorizontalDivider(Modifier.padding(horizontal = 6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            FloatingIcon(
                painterResource(R.drawable.ic_skip_previous), R.string.action_previous, iconSize = 32.dp,
                onClick = onMediaAction?.let { { it("ACTION_MEDIA_PREV") } }
            )
            FloatingIcon(
                painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                if (playing) R.string.action_pause else R.string.action_play,
                iconSize = 32.dp,
                onClick = onMediaAction?.let { { it(if (playing) "ACTION_MEDIA_PAUSE" else "ACTION_MEDIA_PLAY") } }
            )
            FloatingIcon(
                painterResource(R.drawable.ic_skip_next), R.string.action_next, iconSize = 32.dp,
                onClick = onMediaAction?.let { { it("ACTION_MEDIA_NEXT") } }
            )
        }
    }
}

@Composable
private fun Artwork(bitmap: Bitmap, size: Dp, radius: Dp) {
    Image(
        bitmap = remember(bitmap) { bitmap.asImageBitmap() },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(RoundedCornerShape(radius))
    )
}

@Composable
private fun FloatingIcon(
    icon: Painter,
    @StringRes label: Int,
    iconSize: Dp = 22.dp,
    onClick: (() -> Unit)?
) {
    val tint = MiuixTheme.colorScheme.onSurfaceContainer
    if (onClick == null) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(iconSize), tint = tint)
        }
    } else {
        IconButton(onClick = onClick, minWidth = 48.dp, minHeight = 48.dp) {
            Icon(icon, stringResource(label), Modifier.size(iconSize), tint = tint)
        }
    }
}

@Composable
internal fun FloatingLyricsMiuixQuickSettings(
    prefs: SharedPreferences,
    presetColors: IntArray,
    display: FloatingLyricsDisplayConfig,
    blur: Boolean,
    resetPosition: () -> Unit
) {
    val context = LocalContext.current
    val styleStore = remember(context) { FloatingLyricsStyleStore(context) }
    var style by remember { mutableStateOf(styleStore.style) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            style = styleStore.reload()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val colors = MiuixTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)),
        color = floatingPanelColor(blur)
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            QuickSwitch(R.string.settings_floating_follow_album_color, style.followAlbumColor) {
                prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_FOLLOW_ALBUM_COLOR, it) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                presetColors.forEach { value ->
                    val checked = !style.followAlbumColor && style.baseTextColor == value
                    val label = "#%06X".format(value and 0xFFFFFF)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .clickable(role = Role.RadioButton) {
                                    prefs.edit {
                                        putBoolean(FloatingLyricsRenderer.PREF_FOLLOW_ALBUM_COLOR, false)
                                        putInt(FloatingLyricsRenderer.PREF_TEXT_COLOR, value)
                                    }
                                }
                                .semantics { contentDescription = label; selected = checked }
                                .padding(5.dp)
                                .background(Color(value), CircleShape)
                                .border(if (checked) 3.dp else 1.dp, if (checked) colors.primary else colors.outline, CircleShape)
                        )
                    }
                }
            }
            QuickSwitch(R.string.settings_floating_show_album_art, style.showAlbumArt) {
                prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_SHOW_ALBUM_ART, it) }
            }
            QuickSwitch(R.string.settings_floating_text_background, style.enableTextBackground) {
                prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_TEXT_BACKGROUND, it) }
            }
            QuickSwitch(R.string.settings_floating_show_second_line, display.showSecondLine) {
                prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_SHOW_NEIGHBOR_LINE, it) }
            }
            if (display.showSecondLine) {
                Column(Modifier.padding(start = 12.dp)) {
                    BasicComponent(
                        title = stringResource(R.string.settings_floating_secondary_text_mode),
                        summary = stringResource(
                            R.string.settings_floating_secondary_text_mode_summary,
                            display.secondaryTextModes.map { stringResource(secondaryLineLabel(it)) }.joinToString(" → ")
                        )
                    )
                    for (mode in SecondaryTextMode.entries) {
                        val label = secondaryLineLabel(mode)
                        QuickSwitch(label, mode in display.secondaryTextModes) { checked ->
                            val selectedModes = display.secondaryTextModes
                            val modes = if (checked) selectedModes + mode else selectedModes - mode
                            if (modes.isNotEmpty()) SecondaryTextMode.write(prefs, FloatingLyricsRenderer.PREF_SECONDARY_TEXT_MODES, modes)
                        }
                    }
                    QuickSwitch(
                        R.string.settings_floating_split_neighbor_alignment,
                        display.neighborAlignment == FloatingLyricsNeighborAlignment.SPLIT_START_END
                    ) { checked ->
                        val alignment = if (checked) FloatingLyricsNeighborAlignment.SPLIT_START_END else FloatingLyricsNeighborAlignment.CENTER
                        prefs.edit { putString(FloatingLyricsRenderer.PREF_NEIGHBOR_ALIGNMENT, alignment.value) }
                    }
                }
            }
            QuickSwitch(R.string.settings_floating_word_highlight, display.wordHighlight) {
                prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_WORD_HIGHLIGHT, it) }
            }
            QuickSwitch(R.string.settings_floating_text_stroke, style.enableTextStroke) {
                prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_TEXT_STROKE, it) }
            }
            BasicComponent(
                title = stringResource(R.string.settings_floating_text_size),
                summary = stringResource(R.string.floating_settings_text_size_value, style.textSizeSp.toInt()),
                endActions = {
                    IconButton(onClick = { styleStore.adjustTextSize(-2f) }, enabled = style.textSizeSp > 10f) { Text("−") }
                    IconButton(onClick = { styleStore.adjustTextSize(2f) }, enabled = style.textSizeSp < 32f) { Text("+") }
                }
            )
            BasicComponent(
                title = stringResource(R.string.settings_floating_text_weight),
                summary = stringResource(style.textWeight.label),
                endActions = {
                    IconButton(
                        onClick = { styleStore.adjustTextWeight(-1) },
                        enabled = style.textWeight != FloatingLyricsTextWeight.REGULAR
                    ) { Text("−") }
                    IconButton(
                        onClick = { styleStore.adjustTextWeight(1) },
                        enabled = style.textWeight != FloatingLyricsTextWeight.BOLD
                    ) { Text("+") }
                }
            )
            ArrowPreference(title = stringResource(R.string.settings_floating_position_reset), onClick = resetPosition)
        }
    }
}

@Composable
private fun QuickSwitch(@StringRes label: Int, enabled: Boolean, change: (Boolean) -> Unit) {
    SwitchPreference(title = stringResource(label), checked = enabled, onCheckedChange = change)
}

@Composable
private fun floatingPanelColor(blur: Boolean): Color =
    MiuixTheme.colorScheme.surfaceContainer.copy(alpha = if (blur) 0.86f else 1f)

@StringRes
private fun secondaryLineLabel(mode: SecondaryTextMode): Int = when (mode) {
    SecondaryTextMode.NEXT_LYRIC -> R.string.super_island_secondary_text_next_lyric
    SecondaryTextMode.ROMANIZATION -> R.string.super_island_secondary_text_romanization
    SecondaryTextMode.TRANSLATION -> R.string.super_island_secondary_text_translation
}
