package com.example.islandlyrics.feature.settings.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.BackupCategories
import com.example.islandlyrics.ui.miuix.MiuixBlurDialog
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUIX-style dialog for selecting backup categories with per-subgroup checkboxes.
 */
@Composable
fun MiuixBackupCategoryDialog(
    show: Boolean,
    titleRes: Int,
    categories: List<BackupCategories.Category> = BackupCategories.ALL_CATEGORIES,
    categoryKeyCounts: Map<String, Int>? = null,
    initialSelected: Set<String> = categories.flatMap { c ->
        if (c.subGroups.isNotEmpty()) c.subGroups.map { it.id } else listOf(c.id)
    }.toSet(),
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    val checked = remember { mutableStateMapOf<String, Boolean>() }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    val leafItems = categories.flatMap { cat ->
        if (cat.subGroups.isNotEmpty()) cat.subGroups.map { it.id }
        else listOf(cat.id)
    }
    val totalLeafCount = leafItems.size

    LaunchedEffect(initialSelected) {
        checked.clear()
        for (cat in categories) {
            if (cat.subGroups.isNotEmpty()) {
                for (sub in cat.subGroups) {
                    checked[sub.id] = sub.id in initialSelected
                }
                checked[cat.id] = cat.subGroups.all { checked[it.id] == true }
            } else {
                checked[cat.id] = cat.id in initialSelected
            }
        }
    }

    fun onLeafToggle(leafId: String, value: Boolean) {
        checked[leafId] = value
        for (cat in categories) {
            if (cat.subGroups.isNotEmpty()) {
                val subIds = cat.subGroups.map { it.id }
                if (leafId in subIds) {
                    checked[cat.id] = subIds.all { checked[it] == true }
                    break
                }
            }
        }
    }

    fun onParentToggle(cat: BackupCategories.Category, value: Boolean) {
        if (cat.subGroups.isNotEmpty()) {
            checked[cat.id] = value
            for (sub in cat.subGroups) checked[sub.id] = value
        } else {
            checked[cat.id] = value
        }
    }

    fun leafCheckedCount(): Int = leafItems.count { checked[it] == true }
    val allChecked = leafCheckedCount() == totalLeafCount

    MiuixBlurDialog(
        show = true,
        title = stringResource(titleRes),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Select all
            CheckboxPreference(
                title = stringResource(
                    if (allChecked) R.string.backup_dialog_deselect_all
                    else R.string.backup_dialog_select_all
                ),
                checked = allChecked,
                onCheckedChange = { newValue ->
                    for (cat in categories) onParentToggle(cat, newValue)
                }
            )

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))

            // Category list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
            ) {
                items(categories, key = { it.id }) { cat ->
                    val hasSubs = cat.subGroups.isNotEmpty()
                    val isExpanded = expanded[cat.id] ?: false

                    Column {
                        // Parent row
                        CheckboxPreference(
                            title = miuixCategoryLabel(cat.id),
                            checked = if (hasSubs) cat.subGroups.all { checked[it.id] == true } else (checked[cat.id] ?: false),
                            onCheckedChange = { value -> onParentToggle(cat, value) },
                            endActions = if (hasSubs) {
                                {
                                    Text(
                                        text = if (isExpanded) "▲" else "▼",
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .clickable { expanded[cat.id] = !isExpanded }
                                    )
                                }
                            } else null
                        )

                        // Sub-groups with independent checkboxes
                        AnimatedVisibility(
                            visible = isExpanded && hasSubs,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(start = 24.dp)) {
                                for (sub in cat.subGroups) {
                                    CheckboxPreference(
                                        title = (sub.labelOverride ?: miuixSubGroupLabel(sub.id)),
                                        checked = checked[sub.id] ?: false,
                                        onCheckedChange = { onLeafToggle(sub.id, it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.backup_dialog_selected_count,
                    leafCheckedCount(),
                    totalLeafCount
                ),
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = stringResource(R.string.backup_dialog_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.backup_dialog_confirm),
                    onClick = {
                        val result = mutableSetOf<String>()
                        for (cat in categories) {
                            if (cat.subGroups.isNotEmpty()) {
                                for (sub in cat.subGroups) {
                                    if (checked[sub.id] == true) result.add(sub.id)
                                }
                            } else {
                                if (checked[cat.id] == true) result.add(cat.id)
                            }
                        }
                        onConfirm(result)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
fun miuixCategoryLabel(catId: String): String {
    return when (catId) {
        "capsule" -> stringResource(R.string.backup_cat_capsule)
        "notifications" -> stringResource(R.string.backup_cat_notifications)
        "appearance" -> stringResource(R.string.backup_cat_appearance)
        "general" -> stringResource(R.string.backup_cat_general)
        "parser_rules" -> stringResource(R.string.backup_cat_parser_rules)
        "lyric_cache" -> stringResource(R.string.backup_cat_lyric_cache)
        "advanced" -> stringResource(R.string.backup_cat_advanced)
        else -> catId
    }
}

@Composable
fun miuixSubGroupLabel(subId: String): String {
    return when (subId) {
        "capsule_style" -> stringResource(R.string.backup_sub_capsule_style)
        "capsule_layout" -> stringResource(R.string.backup_sub_capsule_layout)
        "capsule_color_share" -> stringResource(R.string.backup_sub_capsule_color_share)
        "notif_behavior" -> stringResource(R.string.backup_sub_notif_behavior)
        "notif_dynamic_icon" -> stringResource(R.string.backup_sub_notif_dynamic_icon)
        "notif_floating" -> stringResource(R.string.backup_sub_notif_floating)
        "notif_appearance" -> stringResource(R.string.backup_sub_notif_appearance)
        "appearance_theme" -> stringResource(R.string.backup_sub_appearance_theme)
        "appearance_blur" -> stringResource(R.string.backup_sub_appearance_blur)
        "appearance_ui_style" -> stringResource(R.string.backup_sub_appearance_ui_style)
        "general_language" -> stringResource(R.string.backup_sub_general_language)
        "general_behavior" -> stringResource(R.string.backup_sub_general_behavior)
        "general_lyric_display" -> stringResource(R.string.backup_sub_general_lyric_display)
        "advanced_lab" -> stringResource(R.string.backup_sub_advanced_lab)
        "advanced_offline" -> stringResource(R.string.backup_sub_advanced_offline)
        "advanced_lyric_dir" -> stringResource(R.string.backup_sub_advanced_lyric_dir)
        "parser_all" -> stringResource(R.string.backup_sub_parser_all)
        else -> subId
    }
}
