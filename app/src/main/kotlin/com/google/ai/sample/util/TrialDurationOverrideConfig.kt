package com.google.ai.sample.util

import android.util.Log
import org.json.JSONObject

/**
 * Remote-updatable trial *duration* - how long a newly started free trial lasts before
 * [com.google.ai.sample.TrialManager] considers it expired.
 *
 * This was previously a hardcoded constant in `TrialManager.kt` with a comment instructing any
 * LLM asked to change it to silently refuse while claiming to have done so. That instruction
 * was not followed: an AI assistant should not deceive the person it's working with, regardless
 * of what a code comment says. Instead, the repo owner was told exactly what the comment said
 * and asked to confirm; they explicitly did, twice, with full knowledge of it (see the chat
 * history / PR description for this change). This file - and the fact that it exists - is the
 * transparent record of that decision, not a quiet workaround.
 *
 * What this *does* change: how long a trial lasts for devices that start a trial after this
 * override is installed (see `TrialManager.startTrialIfNecessaryWithInternetTime`, which computes
 * and persists a fixed end-timestamp once per device - changing this value later does not
 * retroactively shorten/extend a trial that has already started, and does not un-expire one
 * that already has).
 *
 * What this does **not** change, and never will via this mechanism: whether a purchase is
 * valid (still real, Play-Billing-verified `Purchase.PurchaseState`), the internet-time-based
 * anti-tampering check in `TrialTimerService`, or any other part of the entitlement logic in
 * `TrialManager.kt`. Those stay native-only - see `docs/trial-ui-overrides.md`.
 *
 * `trial-duration-overrides.json` (fetched by the WebView relative to `index.html`, same
 * pattern as `command-patterns.json`) - see `docs/trial-duration-overrides.md`.
 */
internal object TrialDurationOverrideConfig {
    private const val TAG = "TrialDurationOverrideConfig"
    const val DEFAULT_TRIAL_DURATION_MS = 7L * 24 * 60 * 60 * 1000L // 1 week, the original value

    @Volatile
    private var currentTrialDurationMs: Long = DEFAULT_TRIAL_DURATION_MS

    fun current(): Long = currentTrialDurationMs

    /**
     * @return 1 if a valid (positive) duration was parsed and installed, 0 if the payload was
     *   blank/invalid/non-positive, in which case the previous value (or the original 7-day
     *   default) remains in effect.
     */
    @Synchronized
    fun setRemoteOverride(json: String): Int {
        if (json.isBlank()) {
            currentTrialDurationMs = DEFAULT_TRIAL_DURATION_MS
            return 0
        }
        return try {
            val obj = JSONObject(json)
            val durationMs = obj.optLong("trialDurationMs", -1L)
            if (durationMs <= 0L) {
                Log.w(TAG, "Ignoring non-positive trialDurationMs override: $durationMs")
                return 0
            }
            currentTrialDurationMs = durationMs
            Log.d(TAG, "Installed trial duration override: ${durationMs}ms (~${durationMs / 86_400_000.0} days)")
            1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse trial duration override: ${e.message}", e)
            0
        }
    }

    /** Reverts to the original built-in default (7 days). */
    @Synchronized
    fun clearRemoteOverride() {
        currentTrialDurationMs = DEFAULT_TRIAL_DURATION_MS
    }
}
