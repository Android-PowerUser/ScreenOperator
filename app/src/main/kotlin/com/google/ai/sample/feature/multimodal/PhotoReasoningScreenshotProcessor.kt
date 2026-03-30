package com.google.ai.sample.feature.multimodal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision

internal class PhotoReasoningScreenshotProcessor {
    private var imageLoader: ImageLoader? = null
    private var imageRequestBuilder: ImageRequest.Builder? = null

    suspend fun loadBitmap(context: Context, screenshotUri: Uri): Bitmap? {
        val loader = imageLoader ?: ImageLoader.Builder(context).build().also { imageLoader = it }
        val builder = imageRequestBuilder ?: ImageRequest.Builder(context).also { imageRequestBuilder = it }

        val imageRequest = builder
            .data(screenshotUri)
            .precision(Precision.EXACT)
            .build()

        val result = loader.execute(imageRequest)
        val successResult = result as? SuccessResult ?: return null
        val bitmapDrawable = successResult.drawable as? BitmapDrawable ?: return null
        return bitmapDrawable.bitmap
    }
}
