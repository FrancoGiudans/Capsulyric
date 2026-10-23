package com.example.islandlyrics.ui.overlay.superisland.state

import com.example.islandlyrics.ui.overlay.model.UIState
import android.os.Parcel
import android.os.Parcelable
import android.content.SharedPreferences
import com.example.islandlyrics.core.settings.AppPreferences
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.focus.model.ActionInfo
import com.xzakota.hyper.notification.focus.model.CoverInfo
import com.xzakota.hyper.notification.focus.model.HighlightInfoV3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/** Regression coverage for the Super Island acceptance findings. */
class SuperIslandAcceptanceRegressionTest {
    private val stillState = UIState(
        title = "song",
        artist = "artist",
        fullLyric = "full current lyric",
        displayLyric = "current lyric",
        progressCurrent = 42,
        isPlaying = false
    )
    // Template 18 uses stable picture keys and a static action title in its JSON.
    // Swapping the referenced bitmap from pause to play does not alter this signature.
    private val unchangedTemplate18Json by lazy { template18FocusJson(true) }

    private data class PictureState(val isPlaying: Boolean) : Parcelable {
        override fun describeContents(): Int = 0
        override fun writeToParcel(dest: Parcel, flags: Int) = Unit
    }

    private fun template18FocusJson(isPlaying: Boolean): String {
        val notification = FocusNotification.createV3 {
            val iconKey = createPicture("miui.focus.pic_template18_action", PictureState(isPlaying))
            coverInfo = CoverInfo().apply {
                title = stillState.fullLyric
                content = "next line"
                subContent = "line after next"
            }
            highlightInfoV3 = HighlightInfoV3().apply {
                primaryText = stillState.title
                secondaryText = stillState.artist
                actionInfo = ActionInfo().apply {
                    type = 0
                    action = "miui.focus.action_template18_play_pause"
                    actionIcon = iconKey
                    actionIconDark = iconKey
                    actionTitle = "Play / pause"
                    clickWithCollapse = false
                }
            }
        }
        return notification.javaClass.getMethod("getFactoryJSON").invoke(notification) as String
    }

    private fun trackerAfterRender(state: UIState): SuperIslandRenderStateTracker {
        return SuperIslandRenderStateTracker().apply {
            resetForStart()
            assertNotNull(prepare(state, state.displayLyric, state.progressCurrent, state.albumColor, "off"))
            markRendered(
                state, state.displayLyric, state.progressCurrent, "song - artist",
                state.albumColor, "off", "same-secondary-rows", unchangedTemplate18Json
            )
            markFirstNotificationSent()
        }
    }

    @Test
    fun pausedCollapsedSettingRefreshMustReachTheBuilder() {
        val tracker = trackerAfterRender(stillState)
        // Match the configuration listener: invalidate both render gates before replaying state.
        tracker.forceNotify("renderConfigChanged")
        val decision = tracker.prepare(
            stillState, stillState.displayLyric, stillState.progressCurrent,
            stillState.albumColor, "off", "same-secondary-rows"
        )
        assertNotNull("A collapsed progress switch refresh is discarded before the builder runs", decision)
        assertFalse(tracker.isDuplicateFocusSignature(unchangedTemplate18Json, false))
    }

    @Test
    fun template18PauseIconChangeMustNotBeDroppedByJsonDeduplication() {
        for (wasPlaying in listOf(true, false)) {
            val previous = stillState.copy(isPlaying = wasPlaying)
            val tracker = trackerAfterRender(previous)
            val changed = previous.copy(isPlaying = !wasPlaying)
            assertNotNull(tracker.prepare(
                changed, changed.displayLyric, changed.progressCurrent,
                changed.albumColor, "off", "same-secondary-rows"
            ))
            val changedJson = template18FocusJson(changed.isPlaying)
            assertEquals("Focus API keeps changed picture data outside its JSON", unchangedTemplate18Json, changedJson)
            assertFalse(
                "The play/pause bitmap changes but its stable JSON key causes the update to be dropped",
                tracker.isDuplicateFocusSignature(changedJson, false)
            )
        }
    }

    @Test
    fun unchangedPausedStateStillUsesBothDeduplicationGates() {
        val tracker = trackerAfterRender(stillState)
        assertEquals(null, tracker.prepare(
            stillState, stillState.displayLyric, stillState.progressCurrent,
            stillState.albumColor, "off", "same-secondary-rows"
        ))
        assertTrue(tracker.isDuplicateFocusSignature(unchangedTemplate18Json, false))
    }

    @Test
    fun advancedStyleMigrationMustPreserveExistingSmallIslandRing() {
        val values = mutableMapOf<String, Any>(
            AppPreferences.Keys.SUPER_ISLAND_NOTIFICATION_STYLE to "advanced_beta",
            AppPreferences.Keys.SUPER_ISLAND_SHOW_PROGRESS_BAR to false
        )
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "putBoolean" -> { values[args!![0] as String] = args[1]!!; editor }
                "apply" -> null
                "commit" -> true
                else -> error(method.name)
            }
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "contains" -> values.containsKey(args!![0] as String)
                "getBoolean", "getString" -> values[args!![0] as String] ?: args[1]
                "edit" -> editor
                else -> error(method.name)
            }
        } as SharedPreferences
        // Before this diff the custom/advanced builder always emitted its small-island ring,
        // even when the standard expanded-progress preference was false.
        assertTrue(
            "Migration must preserve the existing advanced-style small-island ring",
            AppPreferences.isSuperIslandSmallIslandProgressBarEnabled(prefs)
        )
    }
}
