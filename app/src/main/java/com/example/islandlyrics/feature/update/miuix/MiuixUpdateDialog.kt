package com.example.islandlyrics.feature.update.miuix

import com.example.islandlyrics.ui.miuix.MiuixBackHandler
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.update.UpdateChecker
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.islandlyrics.feature.update.UpdateMarkdown
import com.example.islandlyrics.feature.update.UpdateParser
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.example.islandlyrics.ui.miuix.MiuixBlurDialog

@Composable
fun MiuixUpdateDialog(
    show: Boolean,
    releaseInfo: UpdateChecker.ReleaseInfo,
    onDismiss: () -> Unit,
    onIgnore: (String) -> Unit
) {
    val context = LocalContext.current
    
    MiuixBackHandler(enabled = show) {
        onDismiss()
    }
    
    val isChinese = context.resources.configuration.locales[0].language == "zh"
    val changelog = UpdateParser.parseChangelog(releaseInfo.body, isChinese)
        
    val markwon = remember(context) { UpdateMarkdown.create(context) }
    
    val textColor = MiuixTheme.colorScheme.onSurface.toArgb()

    MiuixBlurDialog(
        title = stringResource(R.string.update_available_title, releaseInfo.tagName),
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Scrollable Content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false) // Ensures it doesn't exceed screen but takes only needed space
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        TextView(ctx).apply {
                            setTextColor(textColor)
                            setTextIsSelectable(true)
                            movementMethod = android.text.method.LinkMovementMethod.getInstance()
                        }
                    },
                    update = { textView ->
                        textView.setTextColor(textColor)
                        markwon.setMarkdown(textView, changelog)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Fixed Buttons Row (persistent at the bottom)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = stringResource(R.string.update_ignore),
                    onClick = {
                        onIgnore(releaseInfo.tagName)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    text = stringResource(R.string.update_download),
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.htmlUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}
