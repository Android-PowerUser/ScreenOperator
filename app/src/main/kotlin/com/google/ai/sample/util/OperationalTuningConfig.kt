package com.google.ai.sample.util

/** Built-in native fallback mechanism tuning. WebView owns live tuning for WebView flows. */
internal object OperationalTuningConfig {
    data class Policy(
        val mistralMinIntervalMsDefault: Long = 1500L,
        val mistralMinIntervalMsFastModels: Long = 420L,
        val mistralMaxServerDelayMs: Long = 5_000L,
        val mistralCancelCheckIntervalMs: Long = 100L,
        val modelDownloadMaxRetries: Int = 3,
        val modelDownloadRetryDelayMs: Long = 3_000L,
        val modelDownloadProgressUpdateIntervalMs: Long = 500L,
        val termuxProcessCompletedPrompt: String = "[Process completed - press Enter]",
        val retrievalHeaderPrefix: String = "Retrieved information [",
        val screenElementsMarker: String = "Screen elements:"
    )

    private val policy = Policy()
    fun current(): Policy = policy
}
