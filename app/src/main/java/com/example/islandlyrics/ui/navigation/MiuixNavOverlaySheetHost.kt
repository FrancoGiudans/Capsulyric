/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import java.util.concurrent.atomic.AtomicLong

private sealed interface MiuixOverlayRoute : NavKey {
    data object Base : MiuixOverlayRoute

    data class Sheet(
        val id: Long,
        val entryKey: Any?,
    ) : MiuixOverlayRoute
}

@Composable
internal fun MiuixNavOverlaySheetHost(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onExitComplete: ((Any?) -> Unit)?,
    entryKey: Any?,
    scrimColor: Color,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
    sheetContent: @Composable BoxScope.() -> Unit,
) {
    val idGenerator = remember { AtomicLong() }
    val backStack = remember {
        navBackStackOf(MiuixOverlayRoute.Base).apply {
            if (visible) add(MiuixOverlayRoute.Sheet(idGenerator.incrementAndGet(), entryKey))
        }
    }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentOnExitComplete by rememberUpdatedState(onExitComplete)

    SideEffect {
        val currentSheet = backStack.lastOrNull() as? MiuixOverlayRoute.Sheet
        when {
            visible && currentSheet == null -> {
                backStack.add(MiuixOverlayRoute.Sheet(idGenerator.incrementAndGet(), entryKey))
            }
            visible && currentSheet != null && currentSheet.entryKey !== entryKey -> {
                backStack[backStack.lastIndex] = MiuixOverlayRoute.Sheet(idGenerator.incrementAndGet(), entryKey)
            }
            !visible && currentSheet != null -> backStack.removeAt(backStack.lastIndex)
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { currentOnDismissRequest() },
        modifier = modifier.fillMaxSize().clipToBounds(),
        transition = NavTransitions.MiuixDefault,
        effects = NavDisplayEffects(
            cornerClipRadius = rememberNavSystemCornerRadius(),
            cornerClipMode = NavCornerClipMode.All,
            dimAmount = 0f,
            backdropColor = Color.Transparent,
        ),
    ) {
        entry<MiuixOverlayRoute.Base>(contentKey = { "base" }) {
            val transitionScope = LocalNavTransitionScope.current
            Box(Modifier.fillMaxSize()) {
                content()
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = transitionScope.relativeDepth.coerceIn(0f, 1f) * 0.08f
                        }
                        .background(scrimColor)
                )
            }
        }
        entry<MiuixOverlayRoute.Sheet>(
            contentKey = { it.id },
            transition = NavTransitions.Modal,
        ) { route ->
            MiuixNavOverlaySheetEntry(
                route = route,
                onExitComplete = { currentOnExitComplete?.invoke(it) },
                content = sheetContent,
            )
        }
    }
}

@Composable
private fun MiuixNavOverlaySheetEntry(
    route: MiuixOverlayRoute.Sheet,
    onExitComplete: (Any?) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentOnExitComplete by rememberUpdatedState(onExitComplete)

    DisposableEffect(route.id) {
        onDispose { currentOnExitComplete(route.entryKey) }
    }

    Box(Modifier.fillMaxSize(), content = content)
}
