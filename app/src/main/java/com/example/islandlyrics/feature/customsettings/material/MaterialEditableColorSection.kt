package com.example.islandlyrics.feature.customsettings.material

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import kotlin.math.roundToInt

@Composable
internal fun MaterialEditableColorSection(
    title: String,
    color: Color,
    isEditing: Boolean,
    defaultActionText: String,
    onStartEditing: () -> Unit,
    onColorChanged: (Color) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onUseDefault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(color)
        )
        if (isEditing) {
            MaterialLiteralColorPalette(
                color = color,
                onColorChanged = onColorChanged
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.dialog_btn_cancel))
                }
                OutlinedButton(onClick = onUseDefault) {
                    Text(defaultActionText)
                }
                FilledTonalButton(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.apply))
                }
            }
        } else {
            OutlinedButton(
                onClick = onStartEditing,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.parser_edit))
            }
        }
        Text(
            text = stringResource(
                if (isEditing) {
                    R.string.settings_theme_color_editing_hint
                } else {
                    R.string.settings_theme_color_edit_hint
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MaterialLiteralColorPalette(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var paletteState by remember(color) { mutableStateOf(MaterialPaletteState.fromColor(color)) }

    LaunchedEffect(color.toArgb()) {
        paletteState = MaterialPaletteState.fromColor(color)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(paletteState.color)
        )

        MaterialColorGrid(
            hue = paletteState.hue,
            saturation = paletteState.saturation,
            brightness = paletteState.brightness,
            onStateChange = { hue, saturation, brightness ->
                paletteState = paletteState.copy(hue = hue, saturation = saturation, brightness = brightness)
                onColorChanged(paletteState.color)
            }
        )

        MaterialAlphaSlider(
            hue = paletteState.hue,
            saturation = paletteState.saturation,
            brightness = paletteState.brightness,
            alpha = paletteState.alpha,
            onAlphaChange = { alpha ->
                paletteState = paletteState.copy(alpha = alpha)
                onColorChanged(paletteState.color)
            }
        )
    }
}

@Composable
private fun MaterialColorGrid(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onStateChange: (Float, Float, Float) -> Unit
) {
    val columns = remember {
        listOf(0f, 28f, 52f, 86f, 120f, 148f, 180f, 210f, 242f, 272f, 300f, 330f)
    }
    val rows = remember { listOf(0.96f, 0.92f, 0.99f, 0.90f, 0.72f, 0.50f, 0.24f) }
    val radius = 28.dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    onStateChange(
                        (offset.x / width).coerceIn(0f, 1f) * 360f,
                        ((offset.y / height).coerceIn(0f, 1f) * 0.95f).coerceIn(0.02f, 0.95f),
                        (1f - (offset.y / height).coerceIn(0f, 1f) * 0.85f).coerceIn(0.16f, 1f)
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    onStateChange(
                        (change.position.x / width).coerceIn(0f, 1f) * 360f,
                        ((change.position.y / height).coerceIn(0f, 1f) * 0.95f).coerceIn(0.02f, 0.95f),
                        (1f - (change.position.y / height).coerceIn(0f, 1f) * 0.85f).coerceIn(0.16f, 1f)
                    )
                }
            }
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellWidth = size.width / columns.size
            val cellHeight = size.height / rows.size
            rows.forEachIndexed { rowIndex, rowSaturation ->
                columns.forEachIndexed { columnIndex, cellHue ->
                    val cellValue = when (rowIndex) {
                        0 -> 0.97f
                        1 -> 0.98f
                        2 -> 1f
                        3 -> 0.90f
                        4 -> 0.72f
                        5 -> 0.48f
                        else -> 0.24f
                    }
                    val color = hsvColor(
                        hue = cellHue,
                        saturation = if (rowIndex < 2) rowSaturation * 0.22f else rowSaturation,
                        value = cellValue
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(columnIndex * cellWidth, rowIndex * cellHeight),
                        size = Size(cellWidth + 1f, cellHeight + 1f),
                        cornerRadius = CornerRadius(
                            x = if (columnIndex == 0 || columnIndex == columns.lastIndex) 20f else 0f,
                            y = if (rowIndex == 0 || rowIndex == rows.lastIndex) 20f else 0f
                        )
                    )
                }
            }
        }

        val indicatorRadiusPx = with(density) { 16.dp.roundToPx() }
        val indicatorOffset = remember(hue, saturation, brightness, widthPx, heightPx) {
            IntOffset(
                x = ((hue / 360f) * widthPx).roundToInt() - indicatorRadiusPx,
                y = (((saturation / 0.95f).coerceIn(0f, 1f)) * heightPx).roundToInt() - indicatorRadiusPx
            )
        }
        Box(
            modifier = Modifier
                .offset { indicatorOffset }
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.94f))
                .border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(hsvColor(hue, saturation, brightness))
            )
        }
    }
}

@Composable
private fun MaterialAlphaSlider(
    hue: Float,
    saturation: Float,
    brightness: Float,
    alpha: Float,
    onAlphaChange: (Float) -> Unit
) {
    val previewColor = hsvColor(hue, saturation, brightness, alpha = alpha)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    onAlphaChange((offset.x / width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    onAlphaChange((change.position.x / width).coerceIn(0f, 1f))
                }
            }
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val checkSize = size.height / 4f
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = 0f
                var column = row % 2
                while (x < size.width) {
                    drawRect(
                        color = if (column % 2 == 0) Color(0xFFD7DCE5) else Color(0xFFBFC7D4),
                        topLeft = Offset(x, y),
                        size = Size(checkSize, checkSize)
                    )
                    x += checkSize
                    column++
                }
                y += checkSize
                row++
            }
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        hsvColor(hue, saturation, brightness, alpha = 0f),
                        hsvColor(hue, saturation, brightness, alpha = 1f)
                    )
                ),
                size = size,
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
            )
        }

        val thumbRadiusPx = with(density) { 16.dp.roundToPx() }
        val thumbTopPx = with(density) { (-10).dp.roundToPx() }
        val thumbOffset = remember(alpha, widthPx) {
            IntOffset(
                x = (alpha.coerceIn(0f, 1f) * widthPx).roundToInt() - thumbRadiusPx,
                y = thumbTopPx
            )
        }
        Box(
            modifier = Modifier
                .offset { thumbOffset }
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(
                        width = if (previewColor.luminance() > 0.85f) 1.dp else 0.dp,
                        color = Color.Black.copy(alpha = 0.08f),
                        shape = CircleShape
                    )
            )
        }
    }
}

private data class MaterialPaletteState(
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
    val alpha: Float
) {
    val color: Color
        get() = hsvColor(hue, saturation, brightness, alpha)

    companion object {
        fun fromColor(color: Color): MaterialPaletteState {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(color.copy(alpha = 1f).toArgb(), hsv)
            return MaterialPaletteState(
                hue = hsv[0],
                saturation = hsv[1].coerceIn(0.02f, 0.95f),
                brightness = hsv[2].coerceIn(0.16f, 1f),
                alpha = color.alpha.coerceIn(0f, 1f)
            )
        }
    }
}

private fun hsvColor(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Color {
    return Color(
        AndroidColor.HSVToColor(
            (alpha.coerceIn(0f, 1f) * 255f).roundToInt(),
            floatArrayOf(
                ((hue % 360f) + 360f) % 360f,
                saturation.coerceIn(0f, 1f),
                value.coerceIn(0f, 1f)
            )
        )
    )
}
