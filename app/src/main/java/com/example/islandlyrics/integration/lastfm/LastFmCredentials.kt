/*
 *
 *  * Copyright (c) 2026 Franco Giudance
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

package com.example.islandlyrics.integration.lastfm

data class LastFmCredentials(
    val apiKey: String,
    val apiSecret: String,
    val sessionKey: String? = null,
    val username: String? = null
) {
    val hasApiCredentials: Boolean
        get() = apiKey.isNotBlank() && apiSecret.isNotBlank()

    val isConnected: Boolean
        get() = hasApiCredentials && !sessionKey.isNullOrBlank()
}
