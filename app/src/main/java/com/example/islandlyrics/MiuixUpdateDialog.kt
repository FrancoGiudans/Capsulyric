package com.example.islandlyrics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixUpdateDialog(
    show: MutableState<Boolean>,
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
    val cnHeader = "## \uD83C\uDDE8\uD83C\uDDF3" // 🇨🇳
    val enHeader = "## \uD83C\uDDEC\uD83C\uDDE7" // 🇬🇧
    
    val cnStart = rawBody.indexOf(cnHeader)
    val enStart = rawBody.indexOf(enHeader)
    
    val displayText = if (cnStart != -1 && enStart != -1) {
        if (isChinese) {
            if (cnStart < enStart) {
                rawBody.substring(cnStart + cnHeader.length, enStart).trim()
            } else {
                rawBody.substring(cnStart + cnHeader.length).trim()
            }
        } else {
            if (enStart < cnStart) {
                rawBody.substring(enStart + enHeader.length, cnStart).trim()
            } else {
                rawBody.substring(enStart + enHeader.length).trim()
            }
        }
    } else {
        rawBody
    }

    val changelog = displayText
        .replace(Regex("^\\s*更新日志\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^\\s*Change Log\\s*", RegexOption.MULTILINE), "")
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
    
    val textColor = MiuixTheme.colorScheme.onSurface.toArgb()

    SuperDialog(
        title = stringResource(R.string.update_available_title, releaseInfo.tagName),
        show = show,
        onDismissRequest = {
            show.value = false
            onDismiss()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
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

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = stringResource(R.string.update_ignore),
                    onClick = {
                        onIgnore(releaseInfo.tagName)
                        show.value = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.htmlUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        show.value = false
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.update_download), color = MiuixTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
