package com.google.ai.sample.util

import android.util.Log
import org.json.JSONObject

/**
 * Remote-updatable *text only* for the trial/donation dialogs (titles, body copy, button
 * labels). Deliberately does not touch [com.google.ai.sample.TrialManager] - which `TrialState`
 * a user is in, whether the app is usable, and the trial length stay governed by that file's
 * own logic, not by this config. This only changes what the dialogs *say*.
 *
 * `trial-ui-overrides.json` (fetched by the WebView relative to `index.html`, same pattern as
 * `command-patterns.json`) - see `docs/trial-ui-overrides.md`.
 *
 * Example payload (any subset of fields; omitted ones keep their built-in default):
 * ```json
 * {
 *   "trialExpiredDialogBody": "Enjoying the app? Subscribe to keep using it and support development.",
 *   "paymentMethodGooglePlayButtonLabel": "Google Play (2,90 €/Monat)"
 * }
 * ```
 */
internal object TrialUiConfig {
    private const val TAG = "TrialUiConfig"

    data class Policy(
        val firstLaunchDialogTitle: String = "Trial Information",
        val firstLaunchDialogBody: String =
            "You can try Screen Operator for 7 days before you have to subscribe to support the development of more features.",
        val firstLaunchDialogButton: String = "OK",

        val trialExpiredDialogTitle: String = "Trial period expired",
        val trialExpiredDialogBody: String =
            "Please support the development of the app so that you can continue using it \uD83C\uDF89",
        val trialExpiredDialogSubscribeButton: String = "Subscribe",

        val paymentMethodDialogTitle: String = "Choose Payment Method",
        val paymentMethodPayPalButtonLabel: String = "PayPal (2,90 €/Month)",
        val paymentMethodGooglePlayButtonLabel: String = "Google Play (2,90 €/Month)",
        val paymentMethodCancelButtonLabel: String = "Cancel",

        val infoDialogTitle: String = "Information",

        /** Shown via [com.google.ai.sample.TrialStateUiModel] for the expired state; kept in
         *  sync with [trialExpiredDialogBody] by default so the two copies of this text (the
         *  dialog and the resolver) don't silently drift apart when only one is overridden. */
        val expiredStateInfoMessage: String? = null
    ) {
        fun resolvedExpiredStateInfoMessage(): String = expiredStateInfoMessage ?: trialExpiredDialogBody
    }

    @Volatile
    private var currentPolicy: Policy = Policy()

    fun current(): Policy = currentPolicy

    /**
     * Parses and installs remotely supplied dialog text. Every field is optional; omitted
     * fields keep their current value (so you can override just one string at a time without
     * having to restate the rest). Malformed JSON leaves the previous policy untouched.
     *
     * @return 1 if the payload was successfully parsed and installed, 0 if blank/invalid.
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
                firstLaunchDialogTitle = obj.optStringOrDefault("firstLaunchDialogTitle", base.firstLaunchDialogTitle),
                firstLaunchDialogBody = obj.optStringOrDefault("firstLaunchDialogBody", base.firstLaunchDialogBody),
                firstLaunchDialogButton = obj.optStringOrDefault("firstLaunchDialogButton", base.firstLaunchDialogButton),
                trialExpiredDialogTitle = obj.optStringOrDefault("trialExpiredDialogTitle", base.trialExpiredDialogTitle),
                trialExpiredDialogBody = obj.optStringOrDefault("trialExpiredDialogBody", base.trialExpiredDialogBody),
                trialExpiredDialogSubscribeButton = obj.optStringOrDefault(
                    "trialExpiredDialogSubscribeButton",
                    base.trialExpiredDialogSubscribeButton
                ),
                paymentMethodDialogTitle = obj.optStringOrDefault("paymentMethodDialogTitle", base.paymentMethodDialogTitle),
                paymentMethodPayPalButtonLabel = obj.optStringOrDefault(
                    "paymentMethodPayPalButtonLabel",
                    base.paymentMethodPayPalButtonLabel
                ),
                paymentMethodGooglePlayButtonLabel = obj.optStringOrDefault(
                    "paymentMethodGooglePlayButtonLabel",
                    base.paymentMethodGooglePlayButtonLabel
                ),
                paymentMethodCancelButtonLabel = obj.optStringOrDefault(
                    "paymentMethodCancelButtonLabel",
                    base.paymentMethodCancelButtonLabel
                ),
                infoDialogTitle = obj.optStringOrDefault("infoDialogTitle", base.infoDialogTitle),
                expiredStateInfoMessage = if (obj.has("expiredStateInfoMessage")) {
                    obj.optString("expiredStateInfoMessage").takeIf { it.isNotBlank() }
                } else {
                    base.expiredStateInfoMessage
                }
            )
            Log.d(TAG, "Installed trial UI text override")
            1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse trial UI overrides: ${e.message}", e)
            0
        }
    }

    private fun JSONObject.optStringOrDefault(key: String, default: String): String {
        return if (has(key)) optString(key, default).ifBlank { default } else default
    }

    /** Reverts every dialog string to its built-in default. */
    @Synchronized
    fun clearRemoteOverride() {
        currentPolicy = Policy()
    }
}
