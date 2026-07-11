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

package com.example.islandlyrics.runtime.metadata

import android.content.Context
import com.example.islandlyrics.rules.ParserRuleHelper
import java.util.concurrent.ConcurrentHashMap

class AppNameResolver(
    private val context: Context
) {
    private val cache = ConcurrentHashMap<String, String>()

    fun resolve(packageName: String): String {
        cache[packageName]?.let { return it }

        val customName = ParserRuleHelper.getRuleForPackage(context, packageName)
            ?.customName
            ?.takeIf { it.isNotBlank() }
        if (customName != null) {
            cache[packageName] = customName
            return customName
        }

        return runCatching {
            val packageManager = context.packageManager
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
            .also { name ->
                if (name.isNotEmpty()) {
                    cache[packageName] = name
                }
            }
    }
}
