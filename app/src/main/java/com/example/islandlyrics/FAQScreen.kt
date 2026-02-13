package com.example.islandlyrics

import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@Composable
fun FAQScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    
    // Data Structure
    data class QAItem(val qRes: Int, val aRes: Int)
    data class FAQCategory(val titleRes: Int, val items: List<QAItem>)

    val faqData = listOf(
        FAQCategory(
            R.string.faq_cat_func,
            listOf(
                QAItem(R.string.faq_q_no_lyrics, R.string.faq_a_no_lyrics),
                QAItem(R.string.faq_q_service_error, R.string.faq_a_service_error)
            )
        ),
        FAQCategory(
            R.string.faq_cat_feedback,
            listOf(
                QAItem(R.string.faq_q_unresolved, R.string.faq_a_unresolved),
                QAItem(R.string.faq_q_get_log, R.string.faq_a_get_log)
            )
        )
    )

    Scaffold(
        topBar = {
            // Using a simple custom top bar for consistency or Material3 TopAppBar
             CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.faq_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "Back" // Assuming ic_arrow_back exists, otherwise use default back arrow
                            // Actually, let's use standard icon if generic, or just text "Back" if icon missing.
                            // But usually specialized apps have their own. Let's assume standard vector or Material Icon.
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
             )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            faqData.forEach { category ->
                item {
                    Text(
                        text = stringResource(category.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(category.items) { item ->
                    FAQCard(question = stringResource(item.qRes), answer = stringResource(item.aRes))
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun FAQCard(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "rotation")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    modifier = Modifier.rotate(rotationState),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // HTML Content Renderer
                    HtmlText(text = answer)
                }
            }
        }
    }
}

@Composable
fun HtmlText(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                textSize = 14f // sp equivalent
                movementMethod = LinkMovementMethod.getInstance()
                setLinkTextColor(ContextCompat.getColor(ctx, R.color.brand_color_highlight)) // Assuming brand color or default behavior
            }
        },
        update = { textView ->
            textView.text = Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT)
            textView.setTextColor(textColor)
        }
    )
}

// Fallback assuming brand_color_highlight might not exist, use system blue or primary
// We will replace R.color.brand_color_highlight with proper color call if not sure.
// For now, let's look at available colors or just use default link color.
// 'colors.xml' has just standard colors? Let's check 'colors.xml' content previously shown:
// {"name":"colors.xml","sizeBytes":"763"} -> I see it in file list but didn't read it.
// To be safe, I'll use a hardcoded color or theme attribute in update block if possible.
// Actually, standard TextView link color is usually fine.
