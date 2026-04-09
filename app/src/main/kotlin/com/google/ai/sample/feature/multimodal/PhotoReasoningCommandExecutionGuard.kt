package com.google.ai.sample.feature.multimodal

internal object PhotoReasoningCommandExecutionGuard {
    fun shouldAbort(isJobActive: Boolean, isStopRequested: Boolean): Boolean {
        return !isJobActive || isStopRequested
    }
}
