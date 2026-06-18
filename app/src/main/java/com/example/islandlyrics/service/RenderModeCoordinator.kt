package com.example.islandlyrics.service

import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.ui.common.CapsuleRenderMode
import com.example.islandlyrics.ui.common.LyricCapsuleHandler
import com.example.islandlyrics.ui.common.LyricDisplayManager
import com.example.islandlyrics.ui.common.SuperIslandHandler

class RenderModeCoordinator(
    private val displayManager: LyricDisplayManager,
    private val capsuleHandler: LyricCapsuleHandler?,
    private val superIslandHandler: SuperIslandHandler
) {
    private var mode = CapsuleRenderMode.LIVE_UPDATE

    fun setMode(enabled: Boolean) {
        mode = if (enabled) CapsuleRenderMode.XIAOMI_SUPER_ISLAND else CapsuleRenderMode.LIVE_UPDATE
    }

    fun setMode(mode: CapsuleRenderMode) {
        this.mode = mode
    }

    fun isSuperIslandMode(): Boolean = mode == CapsuleRenderMode.XIAOMI_SUPER_ISLAND

    fun currentMode(): CapsuleRenderMode = mode

    fun updateActiveHandler(shouldRender: Boolean) {
        if (BuildConfig.DEBUG) {
            AppLogger.getInstance().log(
                TAG,
                "[NotifyTrace] coordinator.updateActiveHandler shouldRender=$shouldRender mode=$mode superRunning=${superIslandHandler.isRunning} capsuleRunning=${capsuleHandler?.isRunning()}"
            )
        }
        stopInactiveHandlers()
        when (mode) {
            CapsuleRenderMode.XIAOMI_SUPER_ISLAND -> {
                if (shouldRender && superIslandHandler.isRunning != true) {
                    superIslandHandler.start()
                }
            }
            CapsuleRenderMode.LIVE_UPDATE -> {
                if (shouldRender && capsuleHandler?.isRunning() == false) {
                    capsuleHandler.start()
                }
            }
        }

        if (capsuleHandler?.isRunning() == true ||
            superIslandHandler.isRunning == true) {
            displayManager.start()
        } else {
            displayManager.stop()
        }
    }

    fun stopForCurrentMode() {
        if (BuildConfig.DEBUG) {
            AppLogger.getInstance().log(
                TAG,
                "[NotifyTrace] coordinator.stopForCurrentMode mode=$mode superRunning=${superIslandHandler.isRunning} capsuleRunning=${capsuleHandler?.isRunning()}"
            )
        }
        displayManager.stop()
        when (mode) {
            CapsuleRenderMode.XIAOMI_SUPER_ISLAND -> {
                if (superIslandHandler.isRunning == true) superIslandHandler.stop()
            }
            CapsuleRenderMode.LIVE_UPDATE -> {
                if (capsuleHandler?.isRunning() == true) capsuleHandler.stop()
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
        when (mode) {
            CapsuleRenderMode.XIAOMI_SUPER_ISLAND -> {
                if (superIslandHandler.isRunning == true) superIslandHandler.render(state)
            }
            CapsuleRenderMode.LIVE_UPDATE -> {
                if (capsuleHandler?.isRunning() == true) capsuleHandler.render(state)
            }
        }
    }

    private fun stopInactiveHandlers() {
        if (mode != CapsuleRenderMode.LIVE_UPDATE && capsuleHandler?.isRunning() == true) {
            capsuleHandler.stop()
        }
        if (mode != CapsuleRenderMode.XIAOMI_SUPER_ISLAND && superIslandHandler.isRunning == true) {
            superIslandHandler.stop()
        }
    }

    companion object {
        private const val TAG = "RenderModeCoordinator"
    }
}
