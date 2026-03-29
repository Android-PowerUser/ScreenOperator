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
        private const val MATCH_THRESHOLD = 70
        private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]")
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
            appNameToPackageCache.putAll(manualMappings)
            
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
            appNameVariations.forEach { (baseAppName, variations) ->
                val variationPackageName = getPackageName(baseAppName) ?: return@forEach
                variations.forEach { variation ->
                    appNameToPackageCache[variation] = variationPackageName
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
            val (bestMatch, bestMatchScore) = resolveInfoList
                .map { resolveInfo ->
                    val currentAppName = resolveInfo.loadLabel(packageManager).toString()
                    val normalizedCurrentAppName = normalizeName(currentAppName)
                    resolveInfo to StringSimilarity.calculateMatchScore(normalizedAppName, normalizedCurrentAppName)
                }
                .maxByOrNull { it.second }
                ?: (null to 0)
            
            // If we found a good match, return its package name
            if (bestMatchScore >= MATCH_THRESHOLD && bestMatch != null) {
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
            .replace(NON_ALPHANUMERIC_REGEX, "")
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
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(applicationInfo).toString()
            
            Log.d(TAG, "Found app name for package name '$packageName': $appName")
            
            // Add to cache
            packageToAppNameCache[packageName] = appName
            appNameToPackageCache[normalizeName(appName)] = packageName
            
            return appName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app name for package name '$packageName': ${e.message}")
            return packageName
        }
    }
}
