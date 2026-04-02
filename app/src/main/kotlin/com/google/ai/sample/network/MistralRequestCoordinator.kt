package com.google.ai.sample.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Response
import kotlin.math.max
import kotlin.math.roundToLong

internal data class MistralCoordinatedResponse(
    val response: Response,
    val apiKey: String
)

internal object MistralRequestCoordinator {
    private const val MIN_INTERVAL_MS = 1500L
    private val cooldownMutex = Mutex()
    private val nextAllowedRequestAtMsByKey = mutableMapOf<String, Long>()

    private suspend fun markKeyCooldown(
        key: String,
        referenceTimeMs: Long,
        extraDelayMs: Long = 0L
    ) {
        val nextAllowedAt = referenceTimeMs + max(MIN_INTERVAL_MS, extraDelayMs.coerceAtLeast(0L))
        cooldownMutex.withLock {
            val existing = nextAllowedRequestAtMsByKey[key] ?: 0L
            nextAllowedRequestAtMsByKey[key] = max(existing, nextAllowedAt)
        }
    }

    private suspend fun remainingWaitForKeyMs(key: String, nowMs: Long): Long {
        return cooldownMutex.withLock {
            val nextAllowedAt = nextAllowedRequestAtMsByKey[key] ?: 0L
            (nextAllowedAt - nowMs).coerceAtLeast(0L)
        }
    }

    private fun parseRetryAfterMs(headerValue: String?): Long? {
        if (headerValue.isNullOrBlank()) return null
        val seconds = headerValue.trim().toDoubleOrNull() ?: return null
        return (seconds * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    private fun parseRateLimitResetDelayMs(response: Response, nowMs: Long): Long? {
        val resetHeader = response.header("x-ratelimit-reset") ?: return null
        val resetEpochSeconds = resetHeader.trim().toLongOrNull() ?: return null
        val resetMs = resetEpochSeconds * 1000L
        return (resetMs - nowMs).coerceAtLeast(0L)
    }

    private fun adaptiveRetryDelayMs(failureCount: Int): Long {
        val cappedExponent = (failureCount - 1).coerceIn(0, 5)
        return 1000L shl cappedExponent
    }

    private fun isRetryableFailure(code: Int): Boolean = code == 429 || code >= 500

    suspend fun execute(
        apiKeys: List<String>,
        maxAttempts: Int = apiKeys.size * 4 + 8,
        request: suspend (apiKey: String) -> Response
    ): MistralCoordinatedResponse {
        require(apiKeys.isNotEmpty()) { "No Mistral API keys provided." }

        var consecutiveFailures = 0
        var blockedKeysThisRound = mutableSetOf<String>()

        while (consecutiveFailures < maxAttempts) {
            val now = System.currentTimeMillis()
            val keyPool = apiKeys.filter { it !in blockedKeysThisRound }.ifEmpty {
                blockedKeysThisRound.clear()
                apiKeys
            }

            var selectedKey = apiKeys.first()
            var waitMs = Long.MAX_VALUE
            for (candidate in keyPool) {
                val candidateWait = remainingWaitForKeyMs(candidate, now)
                if (candidateWait < waitMs) {
                    waitMs = candidateWait
                    selectedKey = candidate
                }
            }
            if (waitMs > 0L) {
                delay(waitMs)
            }

            try {
                val response = request(selectedKey)
                val requestEndMs = System.currentTimeMillis()
                val retryAfterMs = parseRetryAfterMs(response.header("Retry-After"))
                val resetDelayMs = parseRateLimitResetDelayMs(response, requestEndMs)
                val serverRequestedDelayMs = max(retryAfterMs ?: 0L, resetDelayMs ?: 0L)
                markKeyCooldown(selectedKey, requestEndMs, serverRequestedDelayMs)

                if (response.isSuccessful || !isRetryableFailure(response.code)) {
                    return MistralCoordinatedResponse(response = response, apiKey = selectedKey)
                }

                response.close()
                blockedKeysThisRound.add(selectedKey)
                consecutiveFailures++
                val adaptiveDelay = adaptiveRetryDelayMs(consecutiveFailures)
                markKeyCooldown(selectedKey, requestEndMs, max(serverRequestedDelayMs, adaptiveDelay))
            } catch (e: Exception) {
                val requestEndMs = System.currentTimeMillis()
                blockedKeysThisRound.add(selectedKey)
                consecutiveFailures++
                markKeyCooldown(selectedKey, requestEndMs, adaptiveRetryDelayMs(consecutiveFailures))
                if (consecutiveFailures >= maxAttempts) throw e
            }
        }

        throw IllegalStateException("Mistral request failed after $maxAttempts attempts.")
    }
}
