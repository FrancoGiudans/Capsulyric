/*
 * This file is part of Capsulyric (IslandLyrics).
 * Portions of this file are derived from or based on compose-miuix-ui/miuix
 * (https://github.com/compose-miuix-ui/miuix), version 0.9.3.
 *
 * Original: Copyright 2025, compose-miuix-ui contributors
 * Original license: Apache License 2.0 (full text: LICENSES/Apache-2.0.txt)
 *
 * Upstream source: miuix-blur: Backdrop, layerBackdrop, rememberLayerBackdrop (miuix-blur/src/commonMain/kotlin/top/yukonga/miuix/kmp/blur/)
 *
 * Modifications by FrancoGiudans for Capsulyric (IslandLyrics):
 *   - Added a local blur-backdrop container and toggles (LocalMiuixBlurBackdrop / LocalMiuixBlurEnabled) on top of miuix-blur APIs.
 */

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
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.ui.miuix.blur

import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * CompositionLocal to provide a [Backdrop] to child components for blur effects.
 */
val LocalMiuixBlurBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * CompositionLocal to provide a global blur enabled state.
 */
val LocalMiuixBlurEnabled = compositionLocalOf { false }

/**
 * CompositionLocal to provide the global MIUIX blur edge highlight state.
 */
val LocalMiuixBlurEdgeHighlightEnabled = compositionLocalOf { false }
