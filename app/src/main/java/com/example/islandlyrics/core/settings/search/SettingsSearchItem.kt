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

package com.example.islandlyrics.core.settings.search

import android.content.Context
import androidx.annotation.StringRes

/**
 * 描述单个可被搜索的设置项。
 */
data class SettingsSearchItem(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int? = null,
    val breadcrumbResList: List<Int>,
    val keywords: List<String> = emptyList(),
    val isVisible: (Context) -> Boolean = { true },
    val action: SettingsSearchAction
)
