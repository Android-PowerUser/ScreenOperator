package com.google.ai.sample

import com.android.billingclient.api.Purchase

internal object MainActivityBillingStateEvaluator {
    fun shouldStartTrialService(state: TrialManager.TrialState): Boolean {
        return state != TrialManager.TrialState.PURCHASED &&
            state != TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED
    }

    fun containsSubscriptionProduct(purchase: Purchase, subscriptionProductId: String): Boolean {
        return purchase.products.any { it == subscriptionProductId }
    }

    fun isPurchasedSubscription(purchase: Purchase, subscriptionProductId: String): Boolean {
        return containsSubscriptionProduct(purchase, subscriptionProductId) &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
    }
}
