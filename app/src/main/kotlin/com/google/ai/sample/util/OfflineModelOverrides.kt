package com.google.ai.sample.util

import android.util.Log
import com.google.ai.sample.ModelOption
import org.json.JSONArray

/**
 * Lets the WebView bundle remotely correct the download metadata - URL, displayed size, and
 * any extra required file URLs - for an existing, built-in offline (on-device) [ModelOption],
 * without requiring a new app release.
 *
 * This exists because the actual offline model files are hosted on third-party services
 * (currently Hugging Face) outside this project's control; a moved/renamed/re-quantized file
 * only needs a JSON edit instead of a code change + release.
 *
 * Example payload (a JSON array, e.g. "offline-model-overrides.json" next to index.html):
 * ```json
 * [
 *   {
 *     "id": "QWEN3_5_4B_OFFLINE",
 *     "downloadUrl": "https://huggingface.co/.../model_multimodal.litertlm?download=true",
 *     "size": "6.3 GB",
 *     "additionalDownloadUrls": ["https://huggingface.co/.../tokenizer.json?download=true"]
 *   }
 * ]
 * ```
 *
 * SAFETY BOUNDARY: this can only replace *where to download from* and *how big the download
 * is displayed as* for an *already existing* offline [ModelOption]. The on-device inference
 * runtime, the filenames it looks for on disk, and whether a model is offline at all stay
 * fixed in compiled code - a remote config cannot turn an online model into an offline one or
 * change what file the inference engine expects to find.
 */
object OfflineModelOverrides {
    private const val TAG = "OfflineModelOverrides"

    data class Override(
        val downloadUrl: String?,
        val size: String?,
        val additionalDownloadUrls: List<String>?
    )

    @Volatile
    private var overrides: Map<String, Override> = emptyMap()

    /**
     * Parses and applies a remote override JSON, replacing any previously applied overrides.
     * Entries referencing an unknown/non-offline [ModelOption] id are skipped (and logged)
     * instead of throwing. Returns the number of overrides applied.
     */
    fun setRemoteOverrides(json: String): Int {
        val parsed = mutableMapOf<String, Override>()
        if (json.isNotBlank()) {
            try {
                val knownOfflineIds = ModelOption.values().filter { it.isOfflineModel }.map { it.name }.toSet()
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val entry = array.optJSONObject(i)
                    if (entry == null) {
                        Log.w(TAG, "Skipping offline model override at index $i: not a JSON object")
                        continue
                    }
                    val id = entry.optString("id", "")
                    if (id.isBlank()) {
                        Log.w(TAG, "Skipping offline model override at index $i: missing id")
                        continue
                    }
                    if (id !in knownOfflineIds) {
                        Log.w(TAG, "Skipping offline model override '$id': not a known built-in offline ModelOption")
                        continue
                    }

                    val downloadUrl = entry.optString("downloadUrl", "").ifBlank { null }
                    val size = entry.optString("size", "").ifBlank { null }
                    val additionalDownloadUrls = entry.optJSONArray("additionalDownloadUrls")?.let { arr ->
                        (0 until arr.length()).mapNotNull { idx -> arr.optString(idx, null) }
                    }

                    if (downloadUrl == null && size == null && additionalDownloadUrls == null) {
                        Log.w(TAG, "Skipping offline model override '$id': no recognized fields set")
                        continue
                    }

                    parsed[id] = Override(downloadUrl, size, additionalDownloadUrls)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse remote offline model overrides: ${e.message}", e)
            }
        }
        overrides = parsed
        return parsed.size
    }

    /** Effective download URL for [option]: remote override if present, else the compiled-in default. */
    fun effectiveDownloadUrl(option: ModelOption): String? =
        overrides[option.name]?.downloadUrl ?: option.downloadUrl

    /** Effective human-readable size label for [option]: remote override if present, else the compiled-in default. */
    fun effectiveSize(option: ModelOption): String? =
        overrides[option.name]?.size ?: option.size

    /** Effective list of additional required-file URLs for [option]: remote override if present, else the compiled-in default. */
    fun effectiveAdditionalDownloadUrls(option: ModelOption): List<String> =
        overrides[option.name]?.additionalDownloadUrls ?: option.additionalDownloadUrls
}
