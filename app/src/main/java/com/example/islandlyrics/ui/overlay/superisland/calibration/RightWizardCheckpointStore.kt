package com.example.islandlyrics.ui.overlay.superisland.calibration

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class RightWizardCheckpoint(
    val profile: RightCalibrationProfile,
    val detailed: Boolean = false,
    val customSample: String = "",
    val answers: List<Boolean> = emptyList()
) {
    fun restore() = RightCalibrationWizard.restore(profile.calibration, detailed, customSample,
        answers, RightCalibrationRuntime::createPolicy)
}

/** Drafts share the device-local, backup-excluded file but never change the selected profile. */
internal class RightWizardCheckpointStore(private val prefs: android.content.SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(RightCalibrationStore.FILE_NAME, Context.MODE_PRIVATE))
    private val profiles = RightCalibrationStore(prefs)

    fun read(ring: Boolean): RightWizardCheckpoint? {
        val saved = decode(prefs.getString(key(ring), null))
        if (saved != null) return saved.takeIf { it.profile.ring == ring }
        // Migrate the old single draft only into its own layout's slot.
        val legacy = decode(prefs.getString(LEGACY_KEY, null))?.takeIf { it.profile.ring == ring }
            ?: return null
        if (write(legacy)) prefs.edit().remove(LEGACY_KEY).commit()
        return legacy
    }

    private fun decode(text: String?): RightWizardCheckpoint? = runCatching {
        if (text == null) return null
        val j = JSONObject(text)
        require(j.getInt("version") == 1)
        val answers = j.getJSONArray("answers")
        require(answers.length() <= 512)
        RightWizardCheckpoint(profiles.readProfile(j.getJSONObject("profile")),
            j.optBoolean("detailed"), Glyphs.limit(j.optString("custom", ""), 160),
            (0 until answers.length()).map { answers.getBoolean(it) })
    }.getOrNull()

    fun write(checkpoint: RightWizardCheckpoint): Boolean {
        val j = JSONObject().apply {
            put("version", 1)
            put("profile", profiles.profileJson(checkpoint.profile))
            put("detailed", checkpoint.detailed)
            put("custom", checkpoint.customSample)
            put("answers", JSONArray(checkpoint.answers))
        }
        // Persist the answer before publishing the next trial, including process death.
        return prefs.edit().putString(key(checkpoint.profile.ring), j.toString()).commit()
    }

    fun clear(ring: Boolean): Boolean {
        val editor = prefs.edit().remove(key(ring))
        if (decode(prefs.getString(LEGACY_KEY, null))?.profile?.ring == ring) editor.remove(LEGACY_KEY)
        return editor.commit()
    }

    companion object {
        private const val LEGACY_KEY = "wizard_checkpoint_v1"
        private fun key(ring: Boolean) = "wizard_checkpoint_v2_${if (ring) "ring" else "plain"}"
    }
}

internal data class RightWizardUiState(
    val running: Boolean = false,
    val readyToBackground: Boolean = false,
    val message: String = ""
)

internal object RightWizardRuntime {
    private val mutable = kotlinx.coroutines.flow.MutableStateFlow(RightWizardUiState())
    val state: kotlinx.coroutines.flow.StateFlow<RightWizardUiState> = mutable
    fun publish(state: RightWizardUiState) { mutable.value = state }
}
