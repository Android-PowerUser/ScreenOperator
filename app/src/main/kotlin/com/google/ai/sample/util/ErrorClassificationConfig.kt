package com.google.ai.sample.util

/** Built-in native fallback AI-provider error classification. WebView owns live API handling. */
internal object ErrorClassificationConfig {
    private val DEFAULT_QUOTA_EXCEEDED_SUBSTRINGS = listOf(
        "exceeded your current quota",
        "code 429",
        "too many requests",
        "rate_limit"
    )

    private val DEFAULT_HIGH_DEMAND_SUBSTRINGS = listOf(
        "service unavailable (503)",
        "unavailable",
        "high demand",
        "overloaded"
    )

    data class Policy(
        val quotaExceededSubstrings: List<String> = DEFAULT_QUOTA_EXCEEDED_SUBSTRINGS,
        val highDemandSubstrings: List<String> = DEFAULT_HIGH_DEMAND_SUBSTRINGS
    ) {
        fun isQuotaExceededError(message: String): Boolean =
            quotaExceededSubstrings.any { message.contains(it, ignoreCase = true) }

        fun isHighDemandError(message: String): Boolean =
            highDemandSubstrings.any { message.contains(it, ignoreCase = true) }
    }

    private val policy = Policy()
    fun current(): Policy = policy
}
