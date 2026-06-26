package com.google.ai.sample.util

import android.util.Log
import org.json.JSONArray

/**
 * A model that does not exist as a compiled-in [com.google.ai.sample.ModelOption] at all.
 * Its definition - which endpoint to call, what the request looks like, whether it sends
 * screenshots - comes entirely from remotely fetched JSON (see [CustomModelConfig]).
 *
 * The actual HTTP call for these models is made from JavaScript inside the WebView (see
 * `window.onCustomModelRequest` in index.html), not from native networking code. That is what
 * lets a genuinely new model/provider be added with zero app release, as long as its API is an
 * OpenAI-compatible chat-completions endpoint reachable via `fetch()` from the WebView (CORS
 * permitting - this must be verified per provider).
 */
data class CustomModelDefinition(
    val id: String,
    val displayName: String,
    val endpoint: String,
    val modelName: String,
    val apiKeyHeader: String = "Authorization",
    val apiKeyPrefix: String = "Bearer ",
    val supportsScreenshot: Boolean = false,
    val supportsTopK: Boolean = false,
    val stream: Boolean = true
)

/**
 * Parses the optional `custom-models.json` file (fetched by the WebView next to index.html)
 * into a list of [CustomModelDefinition]. Malformed entries are skipped (logged) rather than
 * thrown, so a bad config degrades to "no custom models" instead of crashing the app.
 */
internal object CustomModelConfig {
    private const val TAG = "CustomModelConfig"

    fun parse(json: String): List<CustomModelDefinition> {
        val result = mutableListOf<CustomModelDefinition>()
        if (json.isBlank()) return result

        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i)
                if (entry == null) {
                    Log.w(TAG, "Skipping custom model at index $i: not a JSON object")
                    continue
                }

                val id = entry.optString("id", "")
                val endpoint = entry.optString("endpoint", "")
                val modelName = entry.optString("modelName", "")

                if (id.isBlank() || endpoint.isBlank() || modelName.isBlank()) {
                    Log.w(TAG, "Skipping custom model at index $i: 'id', 'endpoint' and 'modelName' are required")
                    continue
                }
                if (!endpoint.startsWith("https://")) {
                    Log.w(TAG, "Skipping custom model '$id': endpoint must be https://")
                    continue
                }

                result.add(
                    CustomModelDefinition(
                        id = id,
                        displayName = entry.optString("displayName", id),
                        endpoint = endpoint,
                        modelName = modelName,
                        apiKeyHeader = entry.optString("apiKeyHeader", "Authorization"),
                        apiKeyPrefix = entry.optString("apiKeyPrefix", "Bearer "),
                        supportsScreenshot = entry.optBoolean("supportsScreenshot", false),
                        supportsTopK = entry.optBoolean("supportsTopK", false),
                        stream = entry.optBoolean("stream", true)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse custom-models.json: ${e.message}", e)
        }
        return result
    }
}
