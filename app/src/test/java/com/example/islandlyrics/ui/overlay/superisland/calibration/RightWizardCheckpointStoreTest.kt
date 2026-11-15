package com.example.islandlyrics.ui.overlay.superisland.calibration

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class RightWizardCheckpointStoreTest {
    private class Memory {
        val values = mutableMapOf<String, Any>()
        private val pending = mutableMapOf<String, Any?>()
        var commitSucceeds = true
        val prefs: SharedPreferences
        init {
            lateinit var editor: SharedPreferences.Editor
            editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, m, a ->
                when (m.name) {
                    "putString" -> { pending[a!![0] as String] = a[1]; editor }
                    "remove" -> { pending[a!![0] as String] = null; editor }
                    "commit", "apply" -> {
                        if (commitSucceeds) pending.forEach { (k,v) -> if (v == null) values.remove(k) else values[k] = v }
                        pending.clear()
                        if (m.name == "commit") commitSucceeds else null
                    }
                    else -> null
                }
            } as SharedPreferences.Editor
            prefs = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, m, a ->
                when (m.name) {
                    "getString" -> values[a!![0]] as? String ?: a[1]
                    "edit" -> editor
                    else -> null
                }
            } as SharedPreferences
        }
    }
    private fun draft() = RightWizardCheckpoint(RightCalibrationProfile(
        name = "我的主题", environment = "device|display|layout", ring = true,
        calibration = RightTextCalibration(overrides = mapOf("👨‍👩‍👧‍👦" to 2f))),
        customSample = "一 W1，二", answers = listOf(true, true, false, true))

    @Test fun processRestartRoundTripsAllProgressAndTheInitialWidthModel() {
        val memory = Memory()
        val before = draft()
        assertTrue(RightWizardCheckpointStore(memory.prefs).write(before))
        assertEquals(before, RightWizardCheckpointStore(memory.prefs).read(true))
    }

    @Test fun unfinishedDraftDoesNotReplaceAnActiveProfile() {
        val memory = Memory()
        val profiles = RightCalibrationStore(memory.prefs)
        val active = draft().profile.copy(name = "已生效")
        profiles.save(active)
        assertTrue(RightWizardCheckpointStore(memory.prefs).write(draft()))
        assertEquals(active, profiles.selected(true))
    }

    @Test fun cancellationOnlyRemovesTheCheckpoint() {
        val memory = Memory()
        val profiles = RightCalibrationStore(memory.prefs)
        val active = draft().profile
        profiles.save(active)
        val checkpoints = RightWizardCheckpointStore(memory.prefs)
        checkpoints.write(draft())
        checkpoints.clear(true)
        assertNull(checkpoints.read(true))
        assertEquals(active, profiles.selected(true))
    }

    @Test fun corruptedOrFutureDraftDoesNotStartASession() {
        val memory = Memory()
        memory.values["wizard_checkpoint_v1"] = "broken json"
        assertNull(RightWizardCheckpointStore(memory.prefs).read(true))
        memory.values["wizard_checkpoint_v1"] = "{\"version\":99}"
        assertNull(RightWizardCheckpointStore(memory.prefs).read(true))
    }

    @Test fun diskFailureIsReportedAndLeavesTheLastDurableCheckpoint() {
        val memory = Memory()
        val checkpoints = RightWizardCheckpointStore(memory.prefs)
        val previous = draft()
        checkpoints.write(previous)
        memory.commitSucceeds = false
        assertFalse(checkpoints.write(previous.copy(answers = previous.answers + true)))
        assertEquals(previous, checkpoints.read(true))
    }

    @Test fun ringAndPlainDraftsResumeIndependentlyAfterProcessRestart() {
        val memory = Memory()
        val withRing = draft()
        val withoutRing = draft().let { it.copy(profile = it.profile.copy(ring = false), answers = listOf(false)) }
        val checkpoints = RightWizardCheckpointStore(memory.prefs)
        checkpoints.write(withRing)
        checkpoints.write(withoutRing)
        val restarted = RightWizardCheckpointStore(memory.prefs)
        assertEquals(withRing, restarted.read(true))
        assertEquals(withoutRing, restarted.read(false))
        restarted.clear(true)
        assertNull(restarted.read(true))
        assertEquals(withoutRing, restarted.read(false))
    }

    @Test fun legacyDraftMigratesOnlyToItsOwnRingState() {
        val memory = Memory()
        val checkpoints = RightWizardCheckpointStore(memory.prefs)
        val old = draft()
        checkpoints.write(old)
        memory.values["wizard_checkpoint_v1"] = memory.values.remove("wizard_checkpoint_v2_ring")!!
        assertNull(checkpoints.read(false))
        assertTrue(memory.values.containsKey("wizard_checkpoint_v1"))
        assertEquals(old, checkpoints.read(true))
        assertFalse(memory.values.containsKey("wizard_checkpoint_v1"))
        assertEquals(old, checkpoints.read(true))
    }

    @Test fun clearingOtherModeDoesNotDiscardALegacyCheckpoint() {
        val memory = Memory()
        val checkpoints = RightWizardCheckpointStore(memory.prefs)
        val old = draft()
        checkpoints.write(old)
        memory.values["wizard_checkpoint_v1"] = memory.values.remove("wizard_checkpoint_v2_ring")!!
        checkpoints.clear(false)
        assertEquals(old, checkpoints.read(true))
    }

    @Test fun selectingOrDisablingOneRingModePreservesTheOtherModesProfile() {
        val memory = Memory()
        val profiles = RightCalibrationStore(memory.prefs)
        val ring = draft().profile
        val plain = ring.copy(id = "plain", ring = false, calibration = RightTextCalibration(capacity = 11f))
        profiles.save(ring)
        profiles.save(plain)
        assertEquals(ring, profiles.selected(true))
        assertEquals(plain, profiles.selected(false))
        profiles.disable(true)
        assertNull(profiles.selected(true))
        assertEquals(plain, profiles.selected(false))
    }
}
