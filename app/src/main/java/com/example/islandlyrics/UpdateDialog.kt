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
        .replace("---", "") // Remove separator lines if caught
        .replace("### ", "\n")
        .replace("## ", "\n")
        .replace("# ", "\n")
        .replace("**", "")
        .replace("__", "")
        .replace("- ", "• ")
        .replace("* ", "• ")
        .trim()

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
                Text(
                    text = changelog,
                    style = MaterialTheme.typography.bodyMedium
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
