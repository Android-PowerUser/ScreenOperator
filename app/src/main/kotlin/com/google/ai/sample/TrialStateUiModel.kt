package com.google.ai.sample

internal data class TrialStateUiModel(
    val infoMessage: String,
    val shouldShowInfoDialog: Boolean
)

internal object TrialStateUiModelResolver {
    fun resolve(state: TrialManager.TrialState): TrialStateUiModel {
        return when (state) {
            TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED -> TrialStateUiModel(
                infoMessage = "Please support the development of the app so that you can continue using it \uD83C\uDF89",
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
