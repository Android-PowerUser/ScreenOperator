package com.google.ai.sample.util

internal object StringSimilarity {
    fun calculateMatchScore(query: String, target: String): Int {
        if (query == target) return 100
        if (target.contains(query)) return 90
        if (query.contains(target)) return 80

        val distance = levenshteinDistance(query, target)
        val maxLength = maxOf(query.length, target.length)
        val similarity = ((maxLength - distance) / maxLength.toFloat()) * 100
        return similarity.toInt()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }
}
