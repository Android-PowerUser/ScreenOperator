package com.google.ai.sample.network

import okhttp3.Call
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class AiCallCancellationHandle {
    private val cancellationRequested = AtomicBoolean(false)
    private val currentCall = AtomicReference<Call?>(null)

    val isCancellationRequested: Boolean
        get() = cancellationRequested.get()

    fun reset() {
        cancellationRequested.set(false)
        currentCall.set(null)
    }

    fun register(call: Call) {
        currentCall.set(call)
        if (cancellationRequested.get()) {
            call.cancel()
        }
    }

    fun clearCurrentCall() {
        currentCall.set(null)
    }

    fun cancel() {
        cancellationRequested.set(true)
        currentCall.get()?.cancel()
    }
}
