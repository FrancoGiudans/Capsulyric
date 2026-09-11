/*
 * Copyright (c) 2026 FrancoGiudans
 *
 * This file is part of Capsulyric.
 */

package com.example.islandlyrics.core.feed

import android.net.Uri

enum class CommunityFeedActionType {
    OPEN_URL
}

enum class CommunityFeedActionStyle {
    PRIMARY,
    SECONDARY
}

data class CommunityFeedAction(
    val id: String,
    val type: CommunityFeedActionType,
    val text: String,
    val url: String,
    val style: CommunityFeedActionStyle
)

fun isSafeCommunityUrl(value: String): Boolean {
    val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    return scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}
