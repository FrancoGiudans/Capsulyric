package com.example.islandlyrics.feature.faq.material

import android.text.method.LinkMovementMethod
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.settings.material.SettingsSectionHeader
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FAQScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    
    data class QAItem(val question: CharSequence, val answer: CharSequence)
    data class FAQCategory(val title: String, val items: List<QAItem>)

    val faqData = remember {
        listOf(
            FAQCategory(
                context.getString(R.string.faq_cat_guide),
                listOfNotNull(
                    QAItem(context.resources.getText(R.string.faq_q_add_rule), context.resources.getText(R.string.faq_a_add_rule)),
                    QAItem(context.resources.getText(R.string.faq_q_config_online_lyrics), context.resources.getText(R.string.faq_a_config_online_lyrics)),
                    QAItem(context.resources.getText(R.string.settings_prerelease_desc), context.resources.getText(R.string.dialog_prerelease_desc_message)),
                    if (com.example.islandlyrics.core.platform.RomUtils.isHyperOsVersionAtLeast(3, 0, 0) || android.os.Build.VERSION.SDK_INT >= 36) {
                        QAItem(context.resources.getText(R.string.faq_q_island_modes), context.resources.getText(R.string.faq_a_island_modes))
                    } else null
                )
            ),
            FAQCategory(
                context.getString(R.string.faq_cat_online),
                listOf(
                    QAItem(context.resources.getText(R.string.faq_q_online_match_wrong), context.resources.getText(R.string.faq_a_online_match_wrong)),
                    QAItem(context.resources.getText(R.string.faq_q_online_match_no_change), context.resources.getText(R.string.faq_a_online_match_no_change)),
                    QAItem(context.resources.getText(R.string.faq_q_clear_online_cache), context.resources.getText(R.string.faq_a_clear_online_cache)),
                    QAItem(context.resources.getText(R.string.faq_q_access_diagnostics), context.resources.getText(R.string.faq_a_access_diagnostics)),
                    QAItem(context.resources.getText(R.string.faq_q_offline_mode_online_lyrics), context.resources.getText(R.string.faq_a_offline_mode_online_lyrics))
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.faq_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = neutralMaterialTopBarColors()
            )
        },
        containerColor = materialPageContainerColor()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            faqData.forEach { category ->
                item {
                    SettingsSectionHeader(text = category.title)
                }
                items(category.items) { item ->
                    FAQItem(question = item.question, answer = item.answer)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun FAQItem(question: CharSequence, answer: CharSequence) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "rotation")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = question.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Expand",
                modifier = Modifier.rotate(rotationState),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                FormattedText(
                    text = answer,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14f
                )
            }
        }
    }
}

@Composable
fun FormattedText(
    text: CharSequence,
    color: androidx.compose.ui.graphics.Color,
    fontSize: Float,
    modifier: Modifier = Modifier
) {
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val textColor = color.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                this.textSize = fontSize
                movementMethod = LinkMovementMethod.getInstance()
                setLinkTextColor(linkColor)
            }
        },
        update = { textView ->
            textView.text = text
            textView.setTextColor(textColor)
            textView.textSize = fontSize
            textView.setLinkTextColor(linkColor)
        }
    )
}
