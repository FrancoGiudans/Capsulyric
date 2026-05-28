package com.example.islandlyrics.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.islandlyrics.R

fun Context.startActivityWithOverlaySheetTransition(intent: Intent) {
    startActivity(intent)
    (this as? Activity)?.overrideActivityTransition(
        Activity.OVERRIDE_TRANSITION_OPEN,
        R.anim.overlay_sheet_open_enter,
        R.anim.overlay_sheet_open_exit
    )
}

fun Activity.applyOverlaySheetCloseTransition() {
    overrideActivityTransition(
        Activity.OVERRIDE_TRANSITION_CLOSE,
        R.anim.overlay_sheet_close_enter,
        R.anim.overlay_sheet_close_exit
    )
}
