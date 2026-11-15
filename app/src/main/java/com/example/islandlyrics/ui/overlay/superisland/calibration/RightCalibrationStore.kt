package com.example.islandlyrics.ui.overlay.superisland.calibration

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class RightCalibrationProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val environment: String,
    val ring: Boolean,
    val calibration: RightTextCalibration
) {
    fun matches(environment: String, ring: Boolean) = this.environment == environment && this.ring == ring
}

/** Separate device-local preferences; the application's export/import never copies these profiles. */
internal class RightCalibrationStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))
    private var cachedJson: String? = null
    private var cachedProfiles = emptyList<RightCalibrationProfile>()

    fun profiles(): List<RightCalibrationProfile> {
        val json = prefs.getString("profiles", "[]") ?: "[]"
        if (json != cachedJson) {
            cachedProfiles = runCatching {
                val array = JSONArray(json)
                (0 until minOf(array.length(), 24)).mapNotNull { index ->
                    runCatching { decode(array.getJSONObject(index)) }.getOrNull()
                }
            }.getOrDefault(emptyList())
            cachedJson = json
        }
        return cachedProfiles
    }

    fun selected(ring: Boolean): RightCalibrationProfile? {
        val selected = prefs.getString("selected_$ring", null) ?: return null
        return profiles().firstOrNull { it.id == selected && it.ring == ring }
    }

    fun save(profile: RightCalibrationProfile) {
        val profiles = (profiles().filterNot { it.id == profile.id } + profile).takeLast(24)
        prefs.edit().putString("profiles", JSONArray().apply {
            profiles.forEach { put(encode(it)) }
        }.toString()).putString("selected_${profile.ring}", profile.id).apply()
    }

    fun disable(ring: Boolean) { prefs.edit().remove("selected_$ring").apply() }

    fun delete(profile: RightCalibrationProfile) {
        val remaining = profiles().filterNot { it.id == profile.id }
        prefs.edit().putString("profiles", JSONArray().apply { remaining.forEach { put(encode(it)) } }.toString())
            .apply {
                if (selected(profile.ring)?.id == profile.id) remove("selected_${profile.ring}")
            }.apply()
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)
    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    internal fun profileJson(profile: RightCalibrationProfile): JSONObject = encode(profile)
    internal fun readProfile(json: JSONObject): RightCalibrationProfile = decode(json)

    private fun encode(p: RightCalibrationProfile) = JSONObject().apply {
        put("version", 1)
        put("id", p.id); put("name", p.name); put("environment", p.environment); put("ring", p.ring)
        val c = p.calibration.sanitized()
        put("capacity", c.capacity); put("margin", c.margin)
        put("letterMonospace", c.letterMonospace); put("letterWidth", c.letterWidth); put("letterScale", c.letterScale)
        put("digitMonospace", c.digitMonospace); put("digitWidth", c.digitWidth); put("digitScale", c.digitScale)
        put("spaceWidth", c.spaceWidth); put("fullSpaceWidth", c.fullSpaceWidth); put("punctuationScale", c.punctuationScale)
        put("overrides", JSONObject(c.overrides as Map<*, *>))
    }

    private fun decode(j: JSONObject): RightCalibrationProfile {
        require(j.getInt("version") == 1)
        val overrides = j.optJSONObject("overrides") ?: JSONObject()
        return RightCalibrationProfile(
            id = j.getString("id"), name = j.getString("name").take(60),
            environment = j.getString("environment"), ring = j.getBoolean("ring"),
            calibration = RightTextCalibration(
                capacity = j.optDouble("capacity", 7.0).toFloat(), margin = j.optDouble("margin", 0.0).toFloat(),
                letterMonospace = j.optBoolean("letterMonospace"), letterWidth = j.optDouble("letterWidth", 0.5).toFloat(),
                letterScale = j.optDouble("letterScale", 1.0).toFloat(), digitMonospace = j.optBoolean("digitMonospace"),
                digitWidth = j.optDouble("digitWidth", 0.5).toFloat(), digitScale = j.optDouble("digitScale", 1.0).toFloat(),
                spaceWidth = j.optDouble("spaceWidth", 0.25).toFloat(), fullSpaceWidth = j.optDouble("fullSpaceWidth", 1.0).toFloat(),
                punctuationScale = j.optDouble("punctuationScale", 1.0).toFloat(),
                overrides = overrides.keys().asSequence().associateWith { overrides.getDouble(it).toFloat() }
            ).sanitized()
        )
    }

    companion object {
        const val FILE_NAME = "SuperIslandRightCalibration"
        fun environment(context: Context): String {
            val resources = context.applicationContext.resources
            val c = resources.configuration
            val m = resources.displayMetrics
            val p = com.example.islandlyrics.core.settings.AppPreferences.of(context)
            val layout = listOf(
                com.example.islandlyrics.core.settings.AppPreferences.superIslandLyricMode(p),
                com.example.islandlyrics.core.settings.AppPreferences.superIslandNotificationStyle(p),
                com.example.islandlyrics.core.settings.AppPreferences.isSuperIslandFullLyricLeftCoverEnabled(p),
                com.example.islandlyrics.core.settings.AppPreferences.isSuperIslandStandardShowLeftCoverEnabled(p)
            ).joinToString(":")
            return listOf(Build.FINGERPRINT, m.widthPixels, m.heightPixels, c.densityDpi,
                c.fontScale, c.orientation, c.locales.toLanguageTags(), layout).joinToString("|")
        }
    }
}

internal object RightCalibrationRuntime {
    private var store: RightCalibrationStore? = null
    private val policies = object : LinkedHashMap<Pair<String, RightTextCalibration>, RightTextWidthPolicy>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, RightTextCalibration>, RightTextWidthPolicy>?) = size > 8
    }
    private val listeners = linkedSetOf<() -> Unit>()
    private val previewListeners = linkedSetOf<() -> Unit>()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changed() }

    fun initialize(context: Context): RightCalibrationStore {
        if (store == null) {
            store = RightCalibrationStore(context).also { it.register(preferenceListener) }
        }
        return store!!
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun changed() { listeners.toList().forEach { it() } }
    fun addPreviewListener(listener: () -> Unit) { previewListeners.add(listener) }
    fun removePreviewListener(listener: () -> Unit) { previewListeners.remove(listener) }
    private fun previewChanged() { previewListeners.toList().forEach { it() } }

    fun configurationChanged(context: Context) {
        if (preview?.environment != RightCalibrationStore.environment(context)) preview = null
        previewChanged()
        changed()
    }

    fun policy(context: Context, ring: Boolean, includePreview: Boolean = true): RightTextWidthPolicy? {
        val store = initialize(context)
        val environment = RightCalibrationStore.environment(context)
        val preview = preview?.takeIf { includePreview && it.ring == ring && it.environment == environment }
        val calibration = preview?.calibration ?: store.selected(ring)
            ?.takeIf { it.matches(environment, ring) }?.calibration ?: return null
        val key = environment to calibration
        return policies.getOrPut(key) { createPolicy(calibration) }
    }

    fun createPolicy(calibration: RightTextCalibration): RightTextWidthPolicy {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 100f
            typeface = Typeface.DEFAULT_BOLD
        }
        val reference = paint.measureText("汉").coerceAtLeast(1f)
        return RightTextWidthPolicy(calibration) { paint.measureText(it) / reference }
    }

    data class Preview(
        val owner: Any, val calibration: RightTextCalibration, val sample: String,
        val raw: Boolean, val ring: Boolean, val environment: String
    )
    var preview: Preview? = null
        private set
    var previewRevision: Long = 0L
        private set
    private var rendererOwner: Any? = null
    private var rendererReady: (() -> Boolean)? = null
    private var refresh: (() -> Unit)? = null

    fun attach(owner: Any, ready: () -> Boolean, refresh: () -> Unit) {
        rendererOwner = owner; rendererReady = ready; this.refresh = refresh
    }
    fun detach(owner: Any) {
        if (rendererOwner != owner) return
        preview = null; rendererOwner = null; rendererReady = null; refresh = null
        previewChanged()
    }
    fun show(preview: Preview): Boolean {
        if (rendererReady?.invoke() != true) return false
        this.preview = preview
        previewRevision++
        previewChanged()
        refresh?.invoke()
        return true
    }
    fun stop(owner: Any) {
        if (preview?.owner != owner) return
        preview = null
        previewRevision++
        previewChanged()
        refresh?.invoke()
    }
    fun previewText(context: Context, ring: Boolean): String? {
        val preview = preview?.takeIf { it.ring == ring && it.environment == RightCalibrationStore.environment(context) }
            ?: return null
        return if (preview.raw) preview.sample else policy(context, ring)?.prefix(preview.sample).orEmpty().ifEmpty { "♪" }
    }
}
