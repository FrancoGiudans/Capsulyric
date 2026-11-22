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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import java.util.concurrent.atomic.AtomicLong

private data class MiuixPageRoute(
    val entryId: Long,
    val pageKey: Any? = null,
    val page: Any? = null,
    val isRoot: Boolean = false,
) : NavKey

private data class MiuixExpandedPageRoute(
    val route: MiuixPageRoute,
    val expansion: PageExpansion,
) : NavKey

private val expandedPageTransition = navGraphicsTransition(opaqueDepth = 2f, scrim = { 0f }) {}

private fun NavKey.pageRoute(): MiuixPageRoute =
    if (this is MiuixExpandedPageRoute) route else this as MiuixPageRoute

@Composable
internal fun <T> MiuixNavPageStackHost(
    stack: List<T>,
    onPop: () -> Unit,
    backdropColor: Color,
    modifier: Modifier = Modifier,
    key: (T) -> Any,
    pageExpansion: (T) -> PageExpansion?,
    backgroundContent: @Composable BoxScope.() -> Unit,
    pageContent: @Composable BoxScope.(T) -> Unit,
) {
    val idGenerator = remember { AtomicLong() }
    fun createRoute(entryId: Long, page: T): NavKey {
        val route = MiuixPageRoute(entryId, key(page), page)
        return pageExpansion(page)?.let { MiuixExpandedPageRoute(route, it) } ?: route
    }
    val backStack = remember {
        navBackStackOf(MiuixPageRoute(entryId = 0L, isRoot = true)).apply {
            stack.forEach { page ->
                add(createRoute(idGenerator.incrementAndGet(), page))
            }
        }
    }
    val targetKeys = stack.map(key)
    val currentOnPop by rememberUpdatedState(onPop)

    SideEffect {
        var matchingPrefixSize = 1
        val commonPageCount = minOf(backStack.size - 1, stack.size)
        while (matchingPrefixSize <= commonPageCount) {
            val stackIndex = matchingPrefixSize - 1
            val route = backStack[matchingPrefixSize].pageRoute()
            if (route.isRoot || route.pageKey != targetKeys[stackIndex]) break
            val page = stack[stackIndex]
            if (route.page !== page) {
                backStack[matchingPrefixSize] = createRoute(route.entryId, page)
            }
            matchingPrefixSize++
        }

        while (backStack.size > matchingPrefixSize) {
            backStack.removeAt(backStack.lastIndex)
        }
        while (matchingPrefixSize - 1 < stack.size) {
            val stackIndex = matchingPrefixSize - 1
            val page = stack[stackIndex]
            backStack.add(createRoute(idGenerator.incrementAndGet(), page))
            matchingPrefixSize++
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { currentOnPop() },
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(backdropColor),
        transition = NavTransitions.MiuixDefault,
        effects = NavDisplayEffects(
            cornerClipRadius = rememberNavSystemCornerRadius(),
            backdropColor = backdropColor,
        ),
    ) {
        entry<MiuixPageRoute>(contentKey = { it.entryId }) { route ->
            Box(Modifier.fillMaxSize()) {
                if (route.isRoot) {
                    backgroundContent()
                } else {
                    @Suppress("UNCHECKED_CAST")
                    pageContent(route.page as T)
                }
            }
        }
        entry<MiuixExpandedPageRoute>(
            contentKey = { it.route.entryId },
            transition = expandedPageTransition,
        ) { entry ->
            val scope = LocalNavTransitionScope.current
            val progress = remember(scope) { { (1f + scope.relativeDepth).coerceIn(0f, 1f) } }
            Box(Modifier.fillMaxSize().expandFrom(entry.expansion, progress)) {
                CompositionLocalProvider(LocalPageExpansionProgress provides progress) {
                    @Suppress("UNCHECKED_CAST")
                    pageContent(entry.route.page as T)
                }
            }
        }
    }
}
