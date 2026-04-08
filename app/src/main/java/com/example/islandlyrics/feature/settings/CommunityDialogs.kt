package com.example.islandlyrics.feature.settings

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.islandlyrics.core.feed.CommunityFeedItem
import com.example.islandlyrics.feature.update.UpdateMarkdown

data class CommunityDialogState(
    val sectionTitle: String,
    val item: CommunityFeedItem
)

fun buildCommunityMarkdown(item: CommunityFeedItem): String {
    return buildList {
        item.summary.takeIf { it.isNotBlank() }?.let { add(it) }
        item.body.takeIf { it.isNotBlank() }?.let { add(it) }
        if (isEmpty() && item.hasUrl) {
            add(item.url)
        }
    }.joinToString("\n\n")
}

@Composable
fun CommunityMarkdownBody(
    markdown: String,
    textColor: Int,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val markwon = remember(context) { UpdateMarkdown.create(context) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            markwon.setMarkdown(textView, markdown)
        }
    )
}
