package com.example.islandlyrics.service

import com.example.islandlyrics.ui.common.LyricCapsuleHandler
import com.example.islandlyrics.ui.common.LyricDisplayManager
import com.example.islandlyrics.ui.common.SuperIslandHandler

class RenderModeCoordinator(
    private val displayManager: LyricDisplayManager,
    private val capsuleHandler: LyricCapsuleHandler?,
    private val superIslandHandler: SuperIslandHandler
) {
    private var isSuperIslandMode = false

    fun setMode(enabled: Boolean) {
        isSuperIslandMode = enabled
    }

    fun isSuperIslandMode(): Boolean = isSuperIslandMode

    fun updateActiveHandler(playing: Boolean, hasLyric: Boolean) {
        if (isSuperIslandMode) {
            if (capsuleHandler?.isRunning() == true) {
                capsuleHandler.stop()
            }
            if (playing && hasLyric && superIslandHandler.isRunning != true) {
                superIslandHandler.start()
            }
        } else {
            if (superIslandHandler.isRunning == true) {
                superIslandHandler.stop()
            }
            if (playing && hasLyric && capsuleHandler?.isRunning() == false) {
                capsuleHandler.start()
            }
        }

        if (capsuleHandler?.isRunning() == true || superIslandHandler.isRunning == true) {
            displayManager.start()
        } else {
            displayManager.stop()
        }
    }

    fun stopForCurrentMode() {
        displayManager.stop()
        if (isSuperIslandMode) {
            if (superIslandHandler.isRunning == true) {
                superIslandHandler.stop()
            }
        } else {
            if (capsuleHandler?.isRunning() == true) {
                capsuleHandler.stop()
            }
        }
    }

    fun stopAll() {
        displayManager.stop()
        if (capsuleHandler?.isRunning() == true) {
            capsuleHandler.stop()
        }
        if (superIslandHandler.isRunning == true) {
            superIslandHandler.stop()
        }
    }

    fun render(state: com.example.islandlyrics.ui.common.UIState) {
        if (isSuperIslandMode) {
            if (superIslandHandler.isRunning == true) {
                superIslandHandler.render(state)
            }
        } else {
            if (capsuleHandler?.isRunning() == true) {
                capsuleHandler.render(state)
            }
        }
    }
}
