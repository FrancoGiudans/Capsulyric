package com.example.islandlyrics.service

import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.core.logging.AppLogger
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

    fun updateActiveHandler(shouldRender: Boolean) {
        if (BuildConfig.DEBUG) {
            AppLogger.getInstance().log(
                TAG,
                "[NotifyTrace] coordinator.updateActiveHandler shouldRender=$shouldRender superMode=$isSuperIslandMode superRunning=${superIslandHandler.isRunning} capsuleRunning=${capsuleHandler?.isRunning()}"
            )
        }
        if (isSuperIslandMode) {
            if (capsuleHandler?.isRunning() == true) {
                capsuleHandler.stop()
            }
            if (shouldRender && superIslandHandler.isRunning != true) {
                superIslandHandler.start()
            }
        } else {
            if (superIslandHandler.isRunning == true) {
                superIslandHandler.stop()
            }
            if (shouldRender && capsuleHandler?.isRunning() == false) {
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
        if (BuildConfig.DEBUG) {
            AppLogger.getInstance().log(
                TAG,
                "[NotifyTrace] coordinator.stopForCurrentMode superMode=$isSuperIslandMode superRunning=${superIslandHandler.isRunning} capsuleRunning=${capsuleHandler?.isRunning()}"
            )
        }
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
        if (BuildConfig.DEBUG) {
            AppLogger.getInstance().log(
                TAG,
                "[NotifyTrace] coordinator.stopAll superRunning=${superIslandHandler.isRunning} capsuleRunning=${capsuleHandler?.isRunning()}"
            )
        }
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

    companion object {
        private const val TAG = "RenderModeCoordinator"
    }
}
