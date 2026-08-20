package com.google.ai.sample

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Server-side Google Play purchase verification client.
 *
 * Sends the purchaseToken to our Deno Deploy backend
 * (screenoperator-purchase-verify), which verifies it directly with
 * Google's servers via the Play Developer API. This is the only
 * reliable defence against Lucky Patcher / Zygisk billing hooks:
 * those tools forge a local Purchase object (purchaseState=PURCHASED)
 * but cannot produce a purchaseToken that Google's servers recognise
 * as valid.
 *
 * Call [verifyPurchase] from a coroutine before calling
 * TrialManager.markAsPurchased(). If verification fails, treat the
 * purchase as invalid regardless of what the local Purchase object says.
 */
object PurchaseVerifier {

    private const val TAG = "PurchaseVerifier"

    // Deno Deploy endpoint — same org as the existing kilo-proxy.
    // The app name is "screenoperator-purchase-verify", so Deno Deploy
    // serves it at this URL.
    private const val VERIFY_URL =
        "https://screenoperator-purchase-verify.android-poweruser.deno.net/verify"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Result of a server-side purchase verification.
     *
     * @param valid       true if Google's servers confirmed the token is genuine.
     * @param reason      human-readable reason when valid=false (for logging).
     * @param expiryMs    subscription expiry in epoch-ms, or null if not available.
     */
    data class VerificationResult(
        val valid: Boolean,
        val reason: String?,
        val expiryMs: Long?,
    )

    /**
     * Verifies a purchase token server-side. Must be called from a coroutine
     * (runs on Dispatchers.IO internally).
     *
     * @param purchaseToken  the token from Purchase.getPurchaseToken()
     * @param productId      the subscription product ID (e.g. "donation_monthly_2_90_eur")
     * @param packageName    the app's package name (usually BuildConfig.APPLICATION_ID)
     * @return VerificationResult indicating whether the purchase is genuine.
     */
    suspend fun verifyPurchase(
        purchaseToken: String,
        productId: String,
        packageName: String,
    ): VerificationResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "verifyPurchase: productId=$productId, token=${purchaseToken.take(20)}...")

        val requestBody = JSONObject().apply {
            put("purchaseToken", purchaseToken)
            put("productId", productId)
            put("packageName", packageName)
        }.toString()

        val request = Request.Builder()
            .url(VERIFY_URL)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.close()

            Log.d(TAG, "verifyPurchase: HTTP ${response.code}, body=${responseBody.take(500)}")

            if (!response.isSuccessful) {
                Log.e(TAG, "verifyPurchase: Server returned HTTP ${response.code}")
                return@withContext VerificationResult(
                    valid = false,
                    reason = "Server returned HTTP ${response.code}",
                    expiryMs = null,
                )
            }

            val json = JSONObject(responseBody)
            val valid = json.optBoolean("valid", false)
            val reason = json.optString("reason", null)

            var expiryMs: Long? = null
            val details = json.optJSONObject("details")
            if (details != null) {
                val expiryStr = details.optString("expiryTimeMillis", "")
                if (expiryStr.isNotEmpty()) {
                    try {
                        expiryMs = expiryStr.toLong()
                    } catch (_: NumberFormatException) {
                        // ignore
                    }
                }
            }

            Log.i(TAG, "verifyPurchase: valid=$valid, reason=$reason, expiryMs=$expiryMs")
            VerificationResult(valid = valid, reason = reason, expiryMs = expiryMs)

        } catch (e: Exception) {
            Log.e(TAG, "verifyPurchase: Network error: ${e.message}", e)
            VerificationResult(
                valid = false,
                reason = "Network error: ${e.message}",
                expiryMs = null,
            )
        }
    }
}
