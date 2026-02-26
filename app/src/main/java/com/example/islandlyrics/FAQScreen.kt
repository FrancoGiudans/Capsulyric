package com.example.islandlyrics

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

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
                listOf(
                    QAItem(context.resources.getText(R.string.faq_q_guide), context.resources.getText(R.string.faq_a_guide)),
                    QAItem(context.resources.getText(R.string.faq_q_super_island), context.resources.getText(R.string.faq_a_super_island))
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
            LargeTopAppBar(
                title = { Text(stringResource(R.string.faq_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = FAQLocalIcons.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = FAQLocalIcons.KeyboardArrowDown,
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

private object FAQLocalIcons {
    val ArrowBack: ImageVector
        get() {
            if (_arrowBack != null) return _arrowBack!!
            _arrowBack = ImageVector.Builder(
                name = "ArrowBack",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(20f, 11f)
                    horizontalLineTo(7.83f)
                    lineTo(13.42f, 5.41f)
                    lineTo(12f, 4f)
                    lineTo(4f, 12f)
                    lineTo(12f, 20f)
                    lineTo(13.41f, 18.59f)
                    lineTo(7.83f, 13f)
                    horizontalLineTo(20f)
                    close()
                }
            }.build()
            return _arrowBack!!
        }
    private var _arrowBack: ImageVector? = null

    val KeyboardArrowDown: ImageVector
        get() {
            if (_keyboardArrowDown != null) return _keyboardArrowDown!!
            _keyboardArrowDown = ImageVector.Builder(
                name = "KeyboardArrowDown",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(7.41f, 8.59f)
                    lineTo(12f, 13.17f)
                    lineTo(16.59f, 8.59f)
                    lineTo(18f, 10f)
                    lineTo(12f, 16f)
                    lineTo(6f, 10f)
                    close()
                }
            }.build()
            return _keyboardArrowDown!!
        }
    private var _keyboardArrowDown: ImageVector? = null
}
