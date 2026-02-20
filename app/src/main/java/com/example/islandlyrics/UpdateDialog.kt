package com.example.islandlyrics

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.fillMaxWidth
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin

@Composable
fun UpdateDialog(
    releaseInfo: UpdateChecker.ReleaseInfo,
    onDismiss: () -> Unit,
    onIgnore: (String) -> Unit
) {
    val context = LocalContext.current
    
    // Language-aware Logic
    val currentLocale = context.resources.configuration.locales[0]
    val isChinese = currentLocale.language == "zh"

    // Parse logic
    val rawBody = releaseInfo.body
    val cnHeader = "## 🇨🇳"
    val enHeader = "## 🇬🇧"
    
    // Attempt to extract sections
    val cnStart = rawBody.indexOf(cnHeader)
    val enStart = rawBody.indexOf(enHeader)
    
    val displayText = if (cnStart != -1 && enStart != -1) {
        if (isChinese) {
            // Extract Chinese section
            if (cnStart < enStart) {
                rawBody.substring(cnStart + cnHeader.length, enStart).trim()
            } else {
                rawBody.substring(cnStart + cnHeader.length).trim()
            }
        } else {
            // Extract English section
            if (enStart < cnStart) {
                rawBody.substring(enStart + enHeader.length, cnStart).trim()
            } else {
                rawBody.substring(enStart + enHeader.length).trim()
            }
        }
    } else {
        // Fallback to full text if headers are missing
        rawBody
    }

    // Markdown Cleaning (applied to the selected section)
    val changelog = displayText
        .replace(Regex("^\\s*更新日志\\s*", RegexOption.MULTILINE), "") // Remove "更新日志" title if present
        .replace(Regex("^\\s*Change Log\\s*", RegexOption.MULTILINE), "") // Remove "Change Log" title if present
        .trim()
        
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(CoilImagesPlugin.create(context))
            .build()
    }
    
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.update_available_title, releaseInfo.tagName),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.htmlUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.update_download))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onIgnore(releaseInfo.tagName)
                onDismiss()
            }) {
                Text(stringResource(R.string.update_ignore))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        }
    )
}
