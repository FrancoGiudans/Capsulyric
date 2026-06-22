package com.example.islandlyrics.ui.miuix.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

@Composable
fun MiuixBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val currentOnBack by rememberUpdatedState(onBack)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = enabled,
        onBackCompleted = { currentOnBack() }
    )
}

