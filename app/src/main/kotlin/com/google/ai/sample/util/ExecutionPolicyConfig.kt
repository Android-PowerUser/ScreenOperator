package com.google.ai.sample.util

/** Built-in native fallback command execution policy. WebView owns the live policy. */
internal object ExecutionPolicyConfig {
    const val DEFAULT_TEMPLATE =
        "Note: this response contained {total} commands, but only the first {executed} were " +
            "executed because more than {limit} commands were sent in a single message " +
            "without an intermediate screenshot. Please send at most {limit} commands per " +
            "message, then wait for the next screenshot before continuing."

    data class Policy(
        val maxCommandsPerMessage: Int = 0,
        val truncationFeedbackTemplate: String = DEFAULT_TEMPLATE,
        val maxRelevantScreenElementMessages: Int = 3
    ) {
        fun formatTruncationFeedback(total: Int, executed: Int): String =
            truncationFeedbackTemplate
                .replace("{total}", total.toString())
                .replace("{executed}", executed.toString())
                .replace("{limit}", maxCommandsPerMessage.toString())
    }

    private val policy = Policy()
    fun current(): Policy = policy
}
