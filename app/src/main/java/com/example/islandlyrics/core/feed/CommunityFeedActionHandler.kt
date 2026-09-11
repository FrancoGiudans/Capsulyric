/*
 * Copyright (c) 2026 FrancoGiudans
 *
 * This file is part of Capsulyric.
 */

package com.example.islandlyrics.core.feed

import android.app.Activity
import android.content.Context
import android.content.Intent

object CommunityFeedActionHandler {
    fun open(context: Context, action: CommunityFeedAction): Boolean {
        if (action.type != CommunityFeedActionType.OPEN_URL || !isSafeCommunityUrl(action.url)) {
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(action.url)).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
