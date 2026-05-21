package com.example.islandlyrics.feature.update.material

import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.update.UpdateChecker
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
import com.example.islandlyrics.feature.settings.ReleaseDialogMode
import com.example.islandlyrics.feature.update.UpdateMarkdown
import com.example.islandlyrics.feature.update.UpdateParser

@Composable
fun UpdateDialog(
    releaseInfo: UpdateChecker.ReleaseInfo,
    mode: ReleaseDialogMode = ReleaseDialogMode.UPDATE_AVAILABLE,
    onDismiss: () -> Unit,
    onIgnore: (String) -> Unit
) {
    val context = LocalContext.current
    val comparableVersion = remember(releaseInfo) { UpdateChecker.getComparableVersion(releaseInfo) }
    
    val isChinese = context.resources.configuration.locales[0].language == "zh"
    val changelog = UpdateParser.parseChangelog(releaseInfo.body, isChinese)
        
    val markwon = remember(context) { UpdateMarkdown.create(context) }
    
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val title = when (mode) {
        ReleaseDialogMode.UPDATE_AVAILABLE -> stringResource(R.string.update_available_title, comparableVersion)
        ReleaseDialogMode.CURRENT_VERSION_CHANGELOG -> stringResource(R.string.update_current_version_changelog_title, comparableVersion)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (mode == ReleaseDialogMode.UPDATE_AVAILABLE) {
                    Text(
                        text = stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
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
                Text(
                    stringResource(
                        if (mode == ReleaseDialogMode.UPDATE_AVAILABLE) {
                            R.string.update_download
                        } else {
                            R.string.update_view_release_page
                        }
                    )
                )
            }
        },
        dismissButton = {
            if (mode == ReleaseDialogMode.UPDATE_AVAILABLE) {
                TextButton(onClick = {
                    onIgnore(comparableVersion)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.update_ignore))
                }
            }
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(
                        if (mode == ReleaseDialogMode.UPDATE_AVAILABLE) {
                            R.string.dialog_btn_cancel
                        } else {
                            R.string.update_close
                        }
                    )
                )
            }
        }
    )
}
