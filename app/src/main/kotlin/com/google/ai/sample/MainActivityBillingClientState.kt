package com.google.ai.sample

import com.android.billingclient.api.BillingClient

internal object MainActivityBillingClientState {
    fun isInitializedAndReady(isInitialized: Boolean, isReady: Boolean): Boolean {
        return isInitialized && isReady
    }

    fun isConnecting(isInitialized: Boolean, connectionState: Int): Boolean {
        return isInitialized && connectionState == BillingClient.ConnectionState.CONNECTING
    }

    fun shouldReconnect(connectionState: Int): Boolean {
        return connectionState == BillingClient.ConnectionState.CLOSED ||
            connectionState == BillingClient.ConnectionState.DISCONNECTED
    }
}
