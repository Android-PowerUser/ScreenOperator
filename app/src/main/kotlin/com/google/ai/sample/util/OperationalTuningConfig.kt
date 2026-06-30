package com.google.ai.sample.util

import android.util.Log
import org.json.JSONObject

/**
 * Remote-updatable tuning for a handful of low-level, easy-to-get-wrong mechanism parameters
 * that previously required a native release to retune: how aggressively the app backs off
 * between Mistral API requests/retries, how it retries a model download, and the exact marker
 * string Termux:Task appends that the app strips out of command output.
 *
 * None of this changes *what* the app does, only *how patiently/quickly* it does it (or, for
 * the Termux marker, what exact text it recognizes) - so it's safe to retune without risking
 * new behavior, only different timing.
 *
 * `operational-tuning-overrides.json` (fetched by the WebView relative to `index.html`, same
 * pattern as `command-patterns.json`) - see `docs/operational-tuning-overrides.md`. Every
 * field is optional; omitted fields keep their built-in default (which matches this app's
 * original hardcoded values).
 */
internal object OperationalTuningConfig {
    private const val TAG = "OperationalTuningConfig"

    data class Policy(
        val mistralMinIntervalMsDefault: Long = 1500L,
        val mistralMinIntervalMsFastModels: Long = 420L,
        val mistralMaxServerDelayMs: Long = 5_000L,
        val mistralCancelCheckIntervalMs: Long = 100L,
        val modelDownloadMaxRetries: Int = 3,
        val modelDownloadRetryDelayMs: Long = 3_000L,
        val modelDownloadProgressUpdateIntervalMs: Long = 500L,
        val termuxProcessCompletedPrompt: String = "[Process completed - press Enter]",
        val retrievalHeaderPrefix: String = "Retrieved information ["
    )

    @Volatile
    private var currentPolicy: Policy = Policy()

    fun current(): Policy = currentPolicy

    /**
     * Parses and installs remotely supplied tuning values. Every field is optional and merged
     * on top of the current policy; negative numbers and blank strings are treated as invalid
     * for that one field and fall back to its current value rather than failing the whole
     * payload. Malformed JSON leaves the previous policy untouched.
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
            currentPolicy = Policy(
                mistralMinIntervalMsDefault = obj.optNonNegativeLong(
                    "mistralMinIntervalMsDefault",
                    base.mistralMinIntervalMsDefault
                ),
                mistralMinIntervalMsFastModels = obj.optNonNegativeLong(
                    "mistralMinIntervalMsFastModels",
                    base.mistralMinIntervalMsFastModels
                ),
                mistralMaxServerDelayMs = obj.optNonNegativeLong("mistralMaxServerDelayMs", base.mistralMaxServerDelayMs),
                mistralCancelCheckIntervalMs = obj.optNonNegativeLong(
                    "mistralCancelCheckIntervalMs",
                    base.mistralCancelCheckIntervalMs
                ),
                modelDownloadMaxRetries = obj.optNonNegativeInt("modelDownloadMaxRetries", base.modelDownloadMaxRetries),
                modelDownloadRetryDelayMs = obj.optNonNegativeLong(
                    "modelDownloadRetryDelayMs",
                    base.modelDownloadRetryDelayMs
                ),
                modelDownloadProgressUpdateIntervalMs = obj.optNonNegativeLong(
                    "modelDownloadProgressUpdateIntervalMs",
                    base.modelDownloadProgressUpdateIntervalMs
                ),
                termuxProcessCompletedPrompt = if (obj.has("termuxProcessCompletedPrompt")) {
                    obj.optString("termuxProcessCompletedPrompt", base.termuxProcessCompletedPrompt)
                        .ifBlank { base.termuxProcessCompletedPrompt }
                } else {
                    base.termuxProcessCompletedPrompt
                },
                retrievalHeaderPrefix = if (obj.has("retrievalHeaderPrefix")) {
                    obj.optString("retrievalHeaderPrefix", base.retrievalHeaderPrefix)
                        .ifBlank { base.retrievalHeaderPrefix }
                } else {
                    base.retrievalHeaderPrefix
                }
            )
            Log.d(TAG, "Installed operational tuning override")
            1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse operational tuning overrides: ${e.message}", e)
            0
        }
    }

    private fun JSONObject.optNonNegativeLong(key: String, default: Long): Long {
        if (!has(key)) return default
        val value = optLong(key, default)
        return if (value < 0L) default else value
    }

    private fun JSONObject.optNonNegativeInt(key: String, default: Int): Int {
        if (!has(key)) return default
        val value = optInt(key, default)
        return if (value < 0) default else value
    }

    /** Reverts every value to its built-in default. */
    @Synchronized
    fun clearRemoteOverride() {
        currentPolicy = Policy()
    }
}
