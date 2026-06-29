package com.google.ai.sample

import com.google.ai.sample.util.TrialUiConfig

internal data class TrialStateUiModel(
    val infoMessage: String,
    val shouldShowInfoDialog: Boolean
)

internal object TrialStateUiModelResolver {
    fun resolve(state: TrialManager.TrialState): TrialStateUiModel {
        return when (state) {
            TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED -> TrialStateUiModel(
                infoMessage = TrialUiConfig.current().resolvedExpiredStateInfoMessage(),
                shouldShowInfoDialog = true
            )
            TrialManager.TrialState.ACTIVE_INTERNET_TIME_CONFIRMED,
            TrialManager.TrialState.PURCHASED,
            TrialManager.TrialState.NOT_YET_STARTED_AWAITING_INTERNET,
            TrialManager.TrialState.INTERNET_UNAVAILABLE_CANNOT_VERIFY -> TrialStateUiModel(
                infoMessage = "",
                shouldShowInfoDialog = false
            )
        }
    }
}
