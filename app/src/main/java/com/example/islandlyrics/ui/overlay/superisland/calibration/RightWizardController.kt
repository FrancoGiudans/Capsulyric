package com.example.islandlyrics.ui.overlay.superisland.calibration

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled

/** The service owns the session; losing the settings Activity must not stop the test. */
internal class RightWizardController(
    private val context: Context,
    private val prepareRenderer: () -> Unit,
    private val restoreRenderer: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val checkpointStore = RightWizardCheckpointStore(context)
    private val profiles = RightCalibrationRuntime.initialize(context)
    private var checkpoint: RightWizardCheckpoint? = null
    private var engine: RightCalibrationWizard? = null
    private var overlay: RightCalibrationControlWindow? = null
    private val preferences = AppPreferences.of(context)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { if (active) validateEnvironment() }
    }
    private var backgrounded = false
    private var resumeCheck = false
    private var token = ""
    private var accepting = false
    private var interactionAt = 0L
    private var frameTask: Runnable? = null
    var active = false
        private set

    private val application get() = context.applicationContext as Application
    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStopped(activity: Activity) {
            if (active && !backgrounded) { backgrounded = true; present() }
        }
        override fun onActivityResumed(activity: Activity) {
            if (active && backgrounded) finish(message = R.string.wizard_paused)
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            if (active && checkpoint?.profile?.environment != RightCalibrationStore.environment(context)) {
                finish(message = R.string.wizard_environment_changed)
            }
        }
        @Deprecated("No longer called by the platform")
        override fun onLowMemory() = Unit
    }
    private val idleCheck = object : Runnable {
        override fun run() {
            if (!active) return
            if (!Settings.canDrawOverlays(context)) finish(message = R.string.wizard_overlay_required)
            else if (SystemClock.elapsedRealtime() - interactionAt >= 120_000L) finish(message = R.string.wizard_paused)
            else if (checkpoint?.profile?.environment != RightCalibrationStore.environment(context) ||
                checkpoint?.profile?.ring != currentRing(context)) finish(message = R.string.wizard_environment_changed)
            else handler.postDelayed(this, 15_000L)
        }
    }

    fun start(): Boolean {
        if (active) return true
        val saved = checkpointStore.read(currentRing(context)) ?: return fail(R.string.wizard_no_draft)
        if (saved.profile.environment != RightCalibrationStore.environment(context) || saved.profile.ring != currentRing(context)) {
            return fail(R.string.wizard_environment_changed)
        }
        if (!Settings.canDrawOverlays(context)) return fail(R.string.wizard_overlay_required)
        checkpoint = saved
        engine = saved.restore()
        active = true
        backgrounded = false
        resumeCheck = saved.answers.isNotEmpty() && engine?.completed != true
        interactionAt = SystemClock.elapsedRealtime()
        return try {
            application.registerActivityLifecycleCallbacks(activityCallbacks)
            context.registerComponentCallbacks(configurationCallbacks)
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
            prepareRenderer()
            overlay = if (isMiuixEnabled(context)) MiuixRightCalibrationOverlay(context, ::answer,
                missing = { interactionAt = SystemClock.elapsedRealtime(); overlay?.showMissing() },
                replay = { interactionAt = SystemClock.elapsedRealtime(); present() },
                back = ::back, pause = { finish(message = R.string.wizard_paused) },
                discard = { finish(discard = true, message = R.string.wizard_discarded) },
                save = ::save, more = ::more) else RightCalibrationOverlay(context, ::answer,
                missing = { interactionAt = SystemClock.elapsedRealtime(); overlay?.showMissing() },
                replay = { interactionAt = SystemClock.elapsedRealtime(); present() },
                back = ::back, pause = { finish(message = R.string.wizard_paused) },
                discard = { finish(discard = true, message = R.string.wizard_discarded) },
                save = ::save, more = ::more)
            overlay?.attach()
            present()
            handler.postDelayed(idleCheck, 15_000L)
            RightWizardRuntime.publish(RightWizardUiState(running = true, readyToBackground = true))
            true
        } catch (_: Exception) {
            finish(message = R.string.wizard_start_failed)
            false
        }
    }

    private fun fail(message: Int): Boolean {
        RightWizardRuntime.publish(RightWizardUiState(message = context.getString(message)))
        return false
    }

    private fun present() {
        if (!active || !validateEnvironment()) return
        handler.removeCallbacksAndMessages(null)
        frameTask = null
        handler.postDelayed(idleCheck, 15_000L)
        val run = engine ?: return
        val c = checkpoint ?: return
        accepting = false
        token = java.util.UUID.randomUUID().toString()
        val currentToken = token
        val probe = if (resumeCheck) CalibrationProbe(CalibrationStage.VISIBILITY, "校准") else run.probe()
        val summary = if (run.completed) context.getString(R.string.wizard_summary,
            run.calibration.capacity.toInt(), run.answers.size) else null
        overlay?.render(probe, currentToken, run.answers.size, c.detailed, c.profile.ring, summary)
        if (summary != null) return
        val frames = if (probe.scrolling) run.scrollingFrames() else listOf(probe.sample)
        var frame = 0
        val task = object : Runnable {
            override fun run() {
                if (!active || token != currentToken || !validateEnvironment()) return
                val shown = RightCalibrationRuntime.show(RightCalibrationRuntime.Preview(
                    this@RightWizardController, run.calibration, frames[frame % frames.size].ifEmpty { "♪" },
                    true, c.profile.ring, c.profile.environment))
                if (!shown) { finish(message = R.string.wizard_start_failed); return }
                frame++
                if (probe.scrolling) handler.postDelayed(this, 1_200L)
            }
        }
        frameTask = task
        task.run()
        val delay = if (probe.scrolling) frames.size * 1_200L else 1_000L
        handler.postDelayed({
            if (active && token == currentToken && backgrounded && !probe.blocked) {
                accepting = true
                overlay?.setAnswering(true)
            }
        }, delay)
    }

    private fun answer(presentationToken: String, complete: Boolean) {
        if (!active || !accepting || presentationToken != token || !validateEnvironment()) return
        accepting = false
        interactionAt = SystemClock.elapsedRealtime()
        if (resumeCheck) {
            if (!complete) { finish(message = R.string.wizard_resume_failed); return }
            resumeCheck = false
        } else {
            val run = engine ?: return
            if (!run.answer(complete)) return
            checkpoint = checkpoint?.copy(answers = run.answers)
            if (!persist()) return
        }
        present()
    }

    private fun back() {
        if (!active) return
        val c = checkpoint ?: return
        if (c.answers.isEmpty()) return
        interactionAt = SystemClock.elapsedRealtime()
        resumeCheck = false
        checkpoint = c.copy(answers = c.answers.dropLast(1))
        engine = checkpoint!!.restore()
        if (persist()) present()
    }

    private fun more() {
        val c = checkpoint ?: return
        if (!active || engine?.completed != true || c.detailed) return
        checkpoint = c.copy(detailed = true)
        engine = checkpoint!!.restore()
        interactionAt = SystemClock.elapsedRealtime()
        if (persist()) present()
    }

    private fun save() {
        if (!active || !validateEnvironment()) return
        val run = engine ?: return
        val c = checkpoint ?: return
        if (!run.completed) return
        profiles.save(c.profile.copy(calibration = run.calibration))
        finish(message = R.string.wizard_saved)
    }

    private fun persist(): Boolean {
        val c = checkpoint ?: return false
        if (checkpointStore.write(c)) return true
        finish(message = R.string.wizard_storage_failed)
        return false
    }

    fun finish(discard: Boolean = false, message: Int = R.string.wizard_paused, restore: Boolean = true) {
        if (!active) return
        active = false
        accepting = false
        handler.removeCallbacksAndMessages(null)
        if (discard) checkpoint?.let { checkpointStore.clear(it.profile.ring) } else checkpoint?.let(checkpointStore::write)
        overlay?.close(); overlay = null
        application.unregisterActivityLifecycleCallbacks(activityCallbacks)
        context.unregisterComponentCallbacks(configurationCallbacks)
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        RightCalibrationRuntime.stop(this)
        RightWizardRuntime.publish(RightWizardUiState(message = context.getString(message)))
        if (restore) restoreRenderer()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun validateEnvironment(): Boolean {
        if (checkpoint?.profile?.matches(RightCalibrationStore.environment(context), currentRing(context)) == true) return true
        finish(message = R.string.wizard_environment_changed)
        return false
    }

    companion object {
        const val ACTION_START = "com.example.islandlyrics.START_RIGHT_CALIBRATION"
        const val ACTION_STOP = "com.example.islandlyrics.STOP_RIGHT_CALIBRATION"
        const val EXTRA_STARTED_FOR_CALIBRATION = "started_for_calibration"
        fun currentRing(context: Context): Boolean =
            com.example.islandlyrics.core.settings.LabFeatureManager.isSuperIslandRightIslandProgressLabEnabled(context) &&
                com.example.islandlyrics.core.settings.AppPreferences.isSuperIslandCollapsedRightProgressRingEnabled(
                    com.example.islandlyrics.core.settings.AppPreferences.of(context))
    }
}
