package com.google.ai.sample.feature.multimodal

import android.content.Context
import com.google.ai.sample.ApiProvider
import com.google.ai.sample.MainActivity

internal object MainActivityBridge {
    fun applicationContextOrNull(): Context? =
        MainActivity.getInstance()?.applicationContext

    fun currentApiKeyOrEmpty(provider: ApiProvider): String =
        MainActivity.getInstance()?.getCurrentApiKey(provider) ?: ""
}
