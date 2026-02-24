package com.example.islandlyrics

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixFAQScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    // FAQ data - reuse the same resource keys as FAQScreen.kt
    data class QAItem(val question: CharSequence, val answer: CharSequence)
    data class FAQCategory(val title: String, val items: List<QAItem>)

    val faqData = remember {
        listOf(
            FAQCategory(
                context.getString(R.string.faq_cat_guide),
                listOf(
                    QAItem(context.resources.getText(R.string.faq_q_guide), context.resources.getText(R.string.faq_a_guide))
                )
            ),
            FAQCategory(
                context.getString(R.string.faq_cat_func),
                listOf(
                    QAItem(context.resources.getText(R.string.faq_q_no_lyrics), context.resources.getText(R.string.faq_a_no_lyrics)),
                    QAItem(context.resources.getText(R.string.faq_q_service_error), context.resources.getText(R.string.faq_a_service_error))
                )
            ),
            FAQCategory(
                context.getString(R.string.faq_cat_feedback),
                listOf(
                    QAItem(context.resources.getText(R.string.faq_q_unresolved), context.resources.getText(R.string.faq_a_unresolved)),
                    QAItem(context.resources.getText(R.string.faq_q_get_log), context.resources.getText(R.string.faq_a_get_log))
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.faq_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            faqData.forEach { category ->
                item {
                    SmallTitle(text = category.title)
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        category.items.forEach { qa ->
                            MiuixFAQItem(question = qa.question, answer = qa.answer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixFAQItem(question: CharSequence, answer: CharSequence) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = question.toString(),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
            Text(
                text = "▼",
                fontSize = 12.sp,
                modifier = Modifier.rotate(rotation),
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
        }

        AnimatedVisibility(visible = expanded) {
            FormattedText(
                text = answer,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14f,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
