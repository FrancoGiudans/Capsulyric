package com.example.islandlyrics

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixParserRuleScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    var rules by remember { mutableStateOf(ParserRuleHelper.loadRules(context)) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ParserRule?>(null) }

    fun refreshRules() {
        rules = ParserRuleHelper.loadRules(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.title_parser_whitelist_manager),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            editingRule = null
                            showEditDialog = true
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add Rule",
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
            if (rules.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        BasicComponent(
                            title = "No Rules",
                            summary = "Tap + to add a parser rule"
                        )
                    }
                }
            } else {
                item {
                    SmallTitle(text = "Parser Rules (${rules.size})")
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        rules.forEach { rule ->
                            SuperSwitch(
                                title = rule.customName ?: rule.packageName,
                                summary = rule.packageName,
                                checked = rule.enabled,
                                onCheckedChange = { enabled ->
                                    val updatedRules = rules.toMutableList()
                                    val index = updatedRules.indexOfFirst { it.packageName == rule.packageName }
                                    if (index >= 0) {
                                        updatedRules[index] = rule.copy(enabled = enabled)
                                        ParserRuleHelper.saveRules(context, updatedRules)
                                        refreshRules()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Dialog (reuse the Material3 one)
    if (showEditDialog) {
        EditRuleDialog(
            rule = editingRule,
            onDismiss = { showEditDialog = false },
            onSave = { newRule ->
                val updatedRules = rules.toMutableList()
                if (editingRule != null) {
                    val index = updatedRules.indexOfFirst { it.packageName == editingRule!!.packageName }
                    if (index >= 0) {
                        updatedRules[index] = newRule
                    }
                } else {
                    updatedRules.add(newRule)
                }
                ParserRuleHelper.saveRules(context, updatedRules)
                refreshRules()
                showEditDialog = false
            }
        )
    }
}
