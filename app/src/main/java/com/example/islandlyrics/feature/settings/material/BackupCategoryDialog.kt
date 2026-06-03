package com.example.islandlyrics.feature.settings.material

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.BackupCategories

/**
 * Material3 dialog for selecting backup categories with per-subgroup checkboxes.
 *
 * Leaf selection: atomic categories use their category ID; expandable categories
 * use their subgroup IDs. The onConfirm callback receives a set of leaf IDs.
 */
@Composable
fun BackupCategoryDialog(
    titleRes: Int,
    categories: List<BackupCategories.Category> = BackupCategories.ALL_CATEGORIES,
    categoryKeyCounts: Map<String, Int>? = null,
    initialSelected: Set<String> = categories.flatMap { c ->
        if (c.subGroups.isNotEmpty()) c.subGroups.map { it.id } else listOf(c.id)
    }.toSet(),
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // Track selection for both categories and subGroups
    val checked = remember { mutableStateMapOf<String, Boolean>() }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // Derive leaf count for display
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
                // Sync parent state
                checked[cat.id] = cat.subGroups.all { checked[it.id] == true }
            } else {
                checked[cat.id] = cat.id in initialSelected
            }
        }
    }

    fun onLeafToggle(leafId: String, value: Boolean) {
        checked[leafId] = value
        // Update parent if this leaf belongs to a subgroup
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
            for (sub in cat.subGroups) {
                checked[sub.id] = value
            }
        } else {
            checked[cat.id] = value
        }
    }

    fun leafCheckedCount(): Int = leafItems.count { checked[it] == true }
    val allChecked = leafCheckedCount() == totalLeafCount
    val someChecked = leafCheckedCount() > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Select all / Deselect all
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newValue = !allChecked
                            for (cat in categories) {
                                onParentToggle(cat, newValue)
                            }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TriStateCheckbox(
                        state = when {
                            allChecked -> ToggleableState.On
                            someChecked -> ToggleableState.Indeterminate
                            else -> ToggleableState.Off
                        },
                        onClick = {
                            val newValue = allChecked || !someChecked
                            for (cat in categories) {
                                onParentToggle(cat, !allChecked && someChecked || newValue)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (allChecked) R.string.backup_dialog_deselect_all
                            else R.string.backup_dialog_select_all
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Category list
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(categories, key = { it.id }) { cat ->
                        val hasSubs = cat.subGroups.isNotEmpty()
                        val isExpanded = expanded[cat.id] ?: false

                        Column {
                            // Parent row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onParentToggle(cat, checked[cat.id] != true) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasSubs) {
                                    val parentState = when {
                                        cat.subGroups.all { checked[it.id] == true } -> ToggleableState.On
                                        cat.subGroups.any { checked[it.id] == true } -> ToggleableState.Indeterminate
                                        else -> ToggleableState.Off
                                    }
                                    TriStateCheckbox(
                                        state = parentState,
                                        onClick = {
                                            val setAll = parentState != ToggleableState.On
                                            for (sub in cat.subGroups) {
                                                checked[sub.id] = setAll
                                            }
                                            checked[cat.id] = setAll
                                        }
                                    )
                                } else {
                                    Checkbox(
                                        checked = checked[cat.id] ?: false,
                                        onCheckedChange = { onParentToggle(cat, it) }
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = categoryLabel(cat.id),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (hasSubs) FontWeight.Medium else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (hasSubs) {
                                    IconButton(onClick = { expanded[cat.id] = !isExpanded }) {
                                        Icon(
                                            imageVector = if (isExpanded)
                                                Icons.Filled.KeyboardArrowUp
                                            else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                                        )
                                    }
                                }
                            }

                            // Sub-groups with independent checkboxes
                            AnimatedVisibility(
                                visible = isExpanded && hasSubs,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(modifier = Modifier.padding(start = 40.dp)) {
                                    for (sub in cat.subGroups) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onLeafToggle(sub.id, checked[sub.id] != true) }
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = checked[sub.id] ?: false,
                                                onCheckedChange = { onLeafToggle(sub.id, it) }
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = sub.labelOverride ?: subGroupLabel(sub.id),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Selected count
                Text(
                    text = stringResource(
                        R.string.backup_dialog_selected_count,
                        leafCheckedCount(),
                        totalLeafCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Collect all selected leaf IDs
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
            }) {
                Text(stringResource(R.string.backup_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_dialog_cancel))
            }
        }
    )
}

/** Resolve category ID → display string. */
@Composable
fun categoryLabel(catId: String): String {
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
fun subGroupLabel(subId: String): String {
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
