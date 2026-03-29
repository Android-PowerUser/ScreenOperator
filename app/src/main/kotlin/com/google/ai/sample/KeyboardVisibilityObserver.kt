package com.google.ai.sample

import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import kotlinx.coroutines.flow.MutableStateFlow

internal class KeyboardVisibilityObserver(
    private val tag: String
) {
    private var onGlobalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    fun start(rootView: View, keyboardState: MutableStateFlow<Boolean>) {
        stop(rootView)
        onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            if (keypadHeight > screenHeight * 0.15) {
                if (!keyboardState.value) {
                    keyboardState.value = true
                    Log.d(tag, "Keyboard visible")
                }
            } else if (keyboardState.value) {
                keyboardState.value = false
                Log.d(tag, "Keyboard hidden")
            }
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
    }

    fun stop(rootView: View) {
        onGlobalLayoutListener?.let {
            rootView.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        onGlobalLayoutListener = null
    }
}
