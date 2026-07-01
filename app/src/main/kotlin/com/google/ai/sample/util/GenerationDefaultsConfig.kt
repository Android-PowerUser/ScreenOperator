package com.google.ai.sample.util

import android.util.Log
import org.json.JSONObject

/**
 * Remote-updatable *factory defaults* for generation settings (temperature/topP/topK) - the
 * values [GenerationSettingsPreferences.loadSettings] falls back to for a model the user has
 * never customized yet. Does not touch the per-model values a user has already saved through
 * the in-app settings UI; those keep working exactly as before and always take precedence.
 *
 * `generation-defaults-overrides.json` (fetched by the WebView relative to `index.html`, same
 * pattern as `command-patterns.json`) - see `docs/generation-defaults-overrides.md`. Useful for
 * shipping a better out-of-the-box default for new installs without a native app release.
 *
 * Example payload:
 * ```json
 * { "temperature": 0.2, "topP": 0.9, "topK": 32 }
 * ```
 */
internal object GenerationDefaultsConfig {
    private const val TAG = "GenerationDefaultsConfig"

    data class Policy(
        val temperature: Float = 0.0f,
        val topP: Float = 0.0f,
        val topK: Int = 1
    )

    @Volatile
    private var currentPolicy: Policy = Policy()

    fun current(): Policy = currentPolicy

    /**
     * Parses and installs remotely supplied generation defaults. Values outside a sane range
     * (temperature 0-2, topP 0-1, topK >= 1 - matching what every supported provider accepts)
     * are rejected for that one field, falling back to the current value, rather than failing
     * the whole payload.
     */
    @Synchronized
    fun setRemoteOverride(json: String): Int {
        if (json.isBlank()) {
            currentPolicy = Policy()
            return 0
        }
        return try {
            val obj = JSONObject(json)
            val base = currentPolicy
            val temperature = obj.optDoubleOrNull("temperature")?.toFloat()
                ?.takeIf { it in 0.0f..2.0f } ?: base.temperature
            val topP = obj.optDoubleOrNull("topP")?.toFloat()
                ?.takeIf { it in 0.0f..1.0f } ?: base.topP
            val topK = if (obj.has("topK")) {
                obj.optInt("topK", base.topK).takeIf { it >= 1 } ?: base.topK
            } else {
                base.topK
            }
            currentPolicy = Policy(temperature = temperature, topP = topP, topK = topK)
            Log.d(TAG, "Installed generation defaults override: temp=$temperature, topP=$topP, topK=$topK")
            1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse generation defaults override: ${e.message}", e)
            0
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        return if (has(key)) optDouble(key, Double.NaN).takeIf { !it.isNaN() } else null
    }

    /** Reverts to the original built-in defaults (temp 0, topP 0, topK 1). */
    @Synchronized
    fun clearRemoteOverride() {
        currentPolicy = Policy()
    }
}
