package com.google.ai.sample.network

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.roundToLong

internal data class MistralCoordinatedResponse(
    val response: Response,
    val apiKey: String
)

internal object MistralRequestCoordinator {
    private const val TAG = "MistralCoordinator"
    private const val MIN_INTERVAL_MS = 1500L
    private const val MAX_SERVER_DELAY_MS = 5_000L
    private val cooldownMutex = Mutex()
    private val nextAllowedRequestAtMsByKey = mutableMapOf<String, Long>()
    private val requestId = AtomicLong(0L)

    private fun keyFingerprint(key: String): String {
        if (key.length <= 8) return key
        return "${key.take(4)}…${key.takeLast(4)}"
    }

    private suspend fun markKeyCooldown(
        key: String,
        referenceTimeMs: Long,
        minIntervalMs: Long,
        extraDelayMs: Long = 0L
    ) {
        val nextAllowedAt = referenceTimeMs + max(minIntervalMs.coerceAtLeast(0L), extraDelayMs.coerceAtLeast(0L))
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
        val raw = resetHeader.trim().toLongOrNull() ?: return null
        val delayMs = when {
            // likely unix epoch in milliseconds
            raw >= 1_000_000_000_000L -> raw - nowMs
            // likely unix epoch in seconds
            raw >= 1_000_000_000L -> (raw * 1000L) - nowMs
            // likely relative seconds
            raw >= 0L -> raw * 1000L
            else -> return null
        }
        return delayMs.coerceAtLeast(0L).coerceAtMost(MAX_SERVER_DELAY_MS)
    }

    private fun adaptiveRetryDelayMs(failureCount: Int): Long {
        val cappedExponent = (failureCount - 1).coerceIn(0, 5)
        return 1000L shl cappedExponent
    }

    private fun isRetryableFailure(code: Int): Boolean = code == 429 || code >= 500

    suspend fun execute(
        apiKeys: List<String>,
        maxAttempts: Int = apiKeys.size * 4 + 8,
        minIntervalMs: Long = MIN_INTERVAL_MS,
        request: suspend (apiKey: String) -> Response
    ): MistralCoordinatedResponse {
        require(apiKeys.isNotEmpty()) { "No Mistral API keys provided." }
        val rid = requestId.incrementAndGet()
        Log.d(TAG, "[$rid] execute start: keys=${apiKeys.size}, maxAttempts=$maxAttempts")

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
            Log.d(
                TAG,
                "[$rid] attempt=${consecutiveFailures + 1}, selectedKey=${keyFingerprint(selectedKey)}, waitMs=$waitMs, blocked=${blockedKeysThisRound.size}"
            )
            if (waitMs > 0L) {
                delay(waitMs)
            }

            try {
                val response = request(selectedKey)
                val requestEndMs = System.currentTimeMillis()
                val retryAfterMs = parseRetryAfterMs(response.header("Retry-After"))
                val resetDelayMs = parseRateLimitResetDelayMs(response, requestEndMs)
                val serverRequestedDelayMs = max(retryAfterMs ?: 0L, resetDelayMs ?: 0L)
                Log.d(
                    TAG,
                    "[$rid] response code=${response.code}, retryAfterMs=${retryAfterMs ?: -1}, resetDelayMs=${resetDelayMs ?: -1}, appliedDelayMs=$serverRequestedDelayMs"
                )
                markKeyCooldown(selectedKey, requestEndMs, minIntervalMs, serverRequestedDelayMs)

                if (response.isSuccessful || !isRetryableFailure(response.code)) {
                    Log.d(TAG, "[$rid] returning response code=${response.code} with key=${keyFingerprint(selectedKey)}")
                    return MistralCoordinatedResponse(response = response, apiKey = selectedKey)
                }

                response.close()
                blockedKeysThisRound.add(selectedKey)
                consecutiveFailures++
                val adaptiveDelay = adaptiveRetryDelayMs(consecutiveFailures)
                Log.w(
                    TAG,
                    "[$rid] retryable failure code=${response.code}, consecutiveFailures=$consecutiveFailures, adaptiveDelay=$adaptiveDelay"
                )
                markKeyCooldown(selectedKey, requestEndMs, minIntervalMs, max(serverRequestedDelayMs, adaptiveDelay))
            } catch (e: Exception) {
                val requestEndMs = System.currentTimeMillis()
                blockedKeysThisRound.add(selectedKey)
                consecutiveFailures++
                Log.e(
                    TAG,
                    "[$rid] exception on key=${keyFingerprint(selectedKey)}, consecutiveFailures=$consecutiveFailures: ${e.message}",
                    e
                )
                markKeyCooldown(selectedKey, requestEndMs, minIntervalMs, adaptiveRetryDelayMs(consecutiveFailures))
                if (consecutiveFailures >= maxAttempts) throw e
            }
        }

        Log.e(TAG, "[$rid] exhausted attempts ($maxAttempts) without success")
        throw IllegalStateException("Mistral request failed after $maxAttempts attempts.")
    }
}
