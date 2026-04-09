package com.google.ai.sample

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
internal fun TrialStateDialogs(
    trialState: TrialManager.TrialState,
    showTrialInfoDialog: Boolean,
    trialInfoMessage: String,
    onDismissTrialInfo: () -> Unit,
    onPurchaseClick: () -> Unit
) {
    when (trialState) {
        TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED -> {
            TrialExpiredDialog(
                onPurchaseClick = onPurchaseClick,
                onDismiss = {}
            )
        }

        TrialManager.TrialState.NOT_YET_STARTED_AWAITING_INTERNET,
        TrialManager.TrialState.INTERNET_UNAVAILABLE_CANNOT_VERIFY -> {
            if (showTrialInfoDialog) {
                InfoDialog(
                    message = trialInfoMessage,
                    onDismiss = onDismissTrialInfo
                )
            }
        }

        TrialManager.TrialState.ACTIVE_INTERNET_TIME_CONFIRMED,
        TrialManager.TrialState.PURCHASED -> Unit
    }
}

@Composable
internal fun PaymentMethodDialog(
    onDismiss: () -> Unit,
    onPayPalClick: () -> Unit,
    onGooglePlayClick: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Payment Method") },
        text = {
            Column {
                Button(
                    onClick = onPayPalClick,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("PayPal (2,60 €/Month)")
                }
                Button(
                    onClick = onGooglePlayClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Google Play (2,90 €/Month)")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun ApiKeyDialogSection(
    apiKeyManager: ApiKeyManager,
    isFirstLaunch: Boolean,
    initialProvider: ApiProvider?,
    onDismiss: () -> Unit
) {
    ApiKeyDialog(
        apiKeyManager = apiKeyManager,
        isFirstLaunch = isFirstLaunch,
        initialProvider = initialProvider,
        onDismiss = onDismiss
    )
}

@Composable
fun FirstLaunchInfoDialog(onDismiss: () -> Unit) {
    Log.d("FirstLaunchInfoDialog", "Composing FirstLaunchInfoDialog")
    Dialog(onDismissRequest = {
        Log.d("FirstLaunchInfoDialog", "onDismissRequest called")
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Trial Information",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "You can try Screen Operator for 7 days before you have to subscribe to support the development of more features.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = {
                        Log.d("FirstLaunchInfoDialog", "OK button clicked")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("OK")
                }
            }
        }
    }
}



@Composable
fun TrialExpiredDialog(
    onPurchaseClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit
) {
    Log.d("TrialExpiredDialog", "Composing TrialExpiredDialog")
    Dialog(onDismissRequest = {
        Log.d("TrialExpiredDialog", "onDismissRequest called (persistent dialog - user tried to dismiss)")
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Trial period expired",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Please support the development of the app so that you can continue using it \uD83C\uDF89",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        Log.d("TrialExpiredDialog", "Purchase button clicked")
                        onPurchaseClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Subscribe")
                }
            }
        }
    }
}

@Composable
fun InfoDialog( 
    message: String,
    onDismiss: () -> Unit
) {
    Log.d("InfoDialog", "Composing InfoDialog with message: $message")
    Dialog(onDismissRequest = {
        Log.d("InfoDialog", "onDismissRequest called")
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Information",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = {
                    Log.d("InfoDialog", "OK button clicked")
                    onDismiss()
                }) {
                    Text("OK")
                }
            }
        }
    }
}
