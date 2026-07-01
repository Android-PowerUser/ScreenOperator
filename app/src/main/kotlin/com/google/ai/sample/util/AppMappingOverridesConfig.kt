package com.google.ai.sample.util

import android.util.Log
import org.json.JSONObject

/**
 * Remote-updatable additions to [AppMappings]: lets the WebView bundle teach `openApp("...")`
 * about new apps (name, package, aliases) or retune the fuzzy-match threshold used to resolve
 * an app name against installed apps, without a native app release.
 *
 * `app-mappings-overrides.json` (fetched by the WebView relative to `index.html`, same pattern
 * as `command-patterns.json`) - see `docs/app-mappings-overrides.md`.
 *
 * Example payload:
 * ```json
 * {
 *   "matchThreshold": 70,
 *   "apps": [
 *     {
 *       "canonicalName": "myneatapp",
 *       "packageName": "com.example.myneatapp",
 *       "variations": ["my neat app", "neat app"],
 *       "aliasesForPackageLookup": []
 *     }
 *   ]
 * }
 * ```
 *
 * Entries are additive and override built-ins with the same `canonicalName`. An empty/missing
 * payload means "no additions, built-in threshold" - i.e. unchanged behavior.
 */
internal object AppMappingOverridesConfig {
    private const val TAG = "AppMappingOverridesConfig"
    const val DEFAULT_MATCH_THRESHOLD = 70

    data class OverrideAppDefinition(
        val canonicalName: String,
        val packageName: String,
        val variations: List<String> = emptyList(),
        val aliasesForPackageLookup: List<String> = emptyList()
    )

    data class Policy(
        val matchThreshold: Int = DEFAULT_MATCH_THRESHOLD,
        val apps: List<OverrideAppDefinition> = emptyList()
    )

    @Volatile
    private var currentPolicy: Policy = Policy()

    fun current(): Policy = currentPolicy

    /**
     * Parses and installs a remotely supplied JSON object of app-mapping overrides. Malformed
     * JSON, or entries missing `canonicalName`/`packageName`, are skipped rather than thrown,
     * so a partially-bad payload still applies the entries that *are* valid.
     *
     * @return the number of valid app entries installed (0 if none/invalid, but a valid
     *   `matchThreshold` is still applied in that case).
     */
    @Synchronized
    fun setRemoteOverride(json: String): Int {
        if (json.isBlank()) {
            currentPolicy = Policy()
            return 0
        }
        return try {
            val obj = JSONObject(json)
            val threshold = obj.optInt("matchThreshold", DEFAULT_MATCH_THRESHOLD)
                .let { if (it < 0 || it > 100) DEFAULT_MATCH_THRESHOLD else it }

            val apps = mutableListOf<OverrideAppDefinition>()
            val appsArray = obj.optJSONArray("apps")
            if (appsArray != null) {
                for (i in 0 until appsArray.length()) {
                    val entry = appsArray.optJSONObject(i) ?: continue
                    val canonicalName = entry.optString("canonicalName").trim()
                    val packageName = entry.optString("packageName").trim()
                    if (canonicalName.isEmpty() || packageName.isEmpty()) {
                        Log.w(TAG, "Skipping app-mapping override entry missing canonicalName/packageName")
                        continue
                    }
                    val variations = entry.optJSONArray("variations")?.let { arr ->
                        (0 until arr.length()).mapNotNull { idx -> arr.optString(idx)?.takeIf { it.isNotBlank() } }
                    } ?: emptyList()
                    val aliases = entry.optJSONArray("aliasesForPackageLookup")?.let { arr ->
                        (0 until arr.length()).mapNotNull { idx -> arr.optString(idx)?.takeIf { it.isNotBlank() } }
                    } ?: emptyList()
                    apps.add(
                        OverrideAppDefinition(
                            canonicalName = canonicalName,
                            packageName = packageName,
                            variations = variations,
                            aliasesForPackageLookup = aliases
                        )
                    )
                }
            }

            currentPolicy = Policy(matchThreshold = threshold, apps = apps)
            Log.d(TAG, "Installed ${apps.size} app-mapping override(s), matchThreshold=$threshold")
            apps.size
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse app-mapping overrides: ${e.message}", e)
            0
        }
    }

    /** Reverts to the built-in defaults (no extra apps, threshold 70). */
    @Synchronized
    fun clearRemoteOverride() {
        currentPolicy = Policy()
    }
}
