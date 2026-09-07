package com.google.ai.sample.util

/** Built-in native fallback trial/donation dialog text. WebView owns the active UI. */
internal object TrialUiConfig {
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
        val expiredStateInfoMessage: String? = null
    ) {
        fun resolvedExpiredStateInfoMessage(): String = expiredStateInfoMessage ?: trialExpiredDialogBody
    }

    private val policy = Policy()
    fun current(): Policy = policy
}
