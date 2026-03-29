package com.google.ai.sample.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class to map between app names and package names
 */
class AppNamePackageMapper(private val context: Context) {
    companion object {
        private const val TAG = "AppNamePackageMapper"
    }
    
    // Cache for app name to package name mappings
    private val appNameToPackageCache = ConcurrentHashMap<String, String>()
    
    // Cache for package name to app name mappings
    private val packageToAppNameCache = ConcurrentHashMap<String, String>()
    
    private val appNameVariations = AppMappings.appNameVariations
    private val manualMappings = AppMappings.manualMappings
    
    /**
     * Initialize the cache with installed apps
     */
    fun initializeCache() {
        Log.d(TAG, "Initializing app name to package name cache")
        
        try {
            appNameToPackageCache.clear()
            packageToAppNameCache.clear()

            val packageManager = context.packageManager
            val resolveInfoList = queryLauncherActivities(packageManager)

            // Add manual mappings once
            for ((key, value) in manualMappings) {
                appNameToPackageCache[key] = value
            }
            
            // Add all apps to the cache
            for (resolveInfo in resolveInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                val appLabel = resolveInfo.loadLabel(packageManager).toString()
                val appName = normalizeName(appLabel)
                
                // Add to caches
                appNameToPackageCache[appName] = packageName
                packageToAppNameCache[packageName] = appLabel
            }

            // Add variations to the cache once
            for ((baseAppName, variations) in appNameVariations) {
                val variationPackageName = getPackageName(baseAppName)
                if (variationPackageName != null) {
                    for (variation in variations) {
                        appNameToPackageCache[variation] = variationPackageName
                    }
                }
            }
            
            Log.d(TAG, "Cache initialized with ${appNameToPackageCache.size} app names and ${packageToAppNameCache.size} package names")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing cache: ${e.message}")
        }
    }
    
    /**
     * Get the package name for an app name
     * 
     * @param appName The app name to look up
     * @return The package name, or null if not found
     */
    fun getPackageName(appName: String): String? {
        val normalizedAppName = normalizeName(appName)
        
        // Check if the app name is already a package name
        if (normalizedAppName.contains(".")) {
            return normalizedAppName
        }
        
        // Check the cache first
        appNameToPackageCache[normalizedAppName]?.let {
            Log.d(TAG, "Found package name in cache for app name '$appName': $it")
            return it
        }
        
        // Check manual mappings
        manualMappings[normalizedAppName]?.let {
            Log.d(TAG, "Found package name in manual mappings for app name '$appName': $it")
            appNameToPackageCache[normalizedAppName] = it
            return it
        }
        
        // Try to find a match in installed apps
        try {
            val packageManager = context.packageManager
            val resolveInfoList = queryLauncherActivities(packageManager)
            
            // Find the best match
            var bestMatch: ResolveInfo? = null
            var bestMatchScore = 0
            
            for (resolveInfo in resolveInfoList) {
                val currentAppName = resolveInfo.loadLabel(packageManager).toString()
                val normalizedCurrentAppName = normalizeName(currentAppName)
                
                // Calculate match score
                val score = calculateMatchScore(normalizedAppName, normalizedCurrentAppName)
                
                if (score > bestMatchScore) {
                    bestMatchScore = score
                    bestMatch = resolveInfo
                }
            }
            
            // If we found a good match, return its package name
            if (bestMatchScore >= 70 && bestMatch != null) { // 70% match threshold
                val packageName = bestMatch.activityInfo.packageName
                Log.d(TAG, "Found package name for app name '$appName': $packageName (match score: $bestMatchScore%)")
                
                // Add to cache
                appNameToPackageCache[normalizedAppName] = packageName
                packageToAppNameCache[packageName] = bestMatch.loadLabel(packageManager).toString()
                
                return packageName
            }
            
            Log.d(TAG, "No good match found for app name '$appName' (best match score: $bestMatchScore%)")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting package name for app name '$appName': ${e.message}")
            return null
        }
    }

    private fun queryLauncherActivities(packageManager: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return packageManager.queryIntentActivities(intent, 0)
    }

    private fun normalizeName(value: String): String {
        return value.lowercase(Locale.getDefault())
            .trim()
            .replace(Regex("[^a-z0-9]"), "")
    }
    
    /**
     * Get the app name for a package name
     * 
     * @param packageName The package name to look up
     * @return The app name, or the package name if not found
     */
    fun getAppName(packageName: String): String {
        // Check the cache first
        packageToAppNameCache[packageName]?.let {
            Log.d(TAG, "Found app name in cache for package name '$packageName': $it")
            return it
        }
        
        // Try to get the app name from the package manager
        try {
            // Get the package manager
            val packageManager = context.packageManager
            
            // Try to get the app info
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(applicationInfo).toString()
            
            Log.d(TAG, "Found app name for package name '$packageName': $appName")
            
            // Add to cache
            packageToAppNameCache[packageName] = appName
            appNameToPackageCache[appName.lowercase(Locale.getDefault())] = packageName
            
            return appName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app name for package name '$packageName': ${e.message}")
            return packageName
        }
    }
    
    /**
     * Calculate a match score between two app names
     * 
     * @param query The query app name
     * @param target The target app name
     * @return A score from 0 to 100 indicating how well the names match
     */
    private fun calculateMatchScore(query: String, target: String): Int {
        // Exact match
        if (query == target) {
            return 100
        }
        
        // Target contains query
        if (target.contains(query)) {
            return 90
        }
        
        // Query contains target
        if (query.contains(target)) {
            return 80
        }
        
        // Calculate Levenshtein distance
        val distance = levenshteinDistance(query, target)
        val maxLength = maxOf(query.length, target.length)
        
        // Convert distance to similarity percentage
        val similarity = ((maxLength - distance) / maxLength.toFloat()) * 100
        
        return similarity.toInt()
    }
    
    /**
     * Calculate the Levenshtein distance between two strings
     * 
     * @param s1 The first string
     * @param s2 The second string
     * @return The Levenshtein distance
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        
        // Create a matrix of size (m+1) x (n+1)
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        // Initialize the matrix
        for (i in 0..m) {
            dp[i][0] = i
        }
        
        for (j in 0..n) {
            dp[0][j] = j
        }
        
        // Fill the matrix
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1, // deletion
                    dp[i][j - 1] + 1, // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[m][n]
    }
}
