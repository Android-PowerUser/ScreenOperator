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
import androidx.compose.ui.graphics.Color
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
    val ui = com.google.ai.sample.util.TrialUiConfig.current()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ui.paymentMethodDialogTitle) },
        text = {
            Column {
                Button(
                    onClick = onPayPalClick,
                    // Do not actually disable this button; keep click behavior enabled.
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color.Gray
                    )
                ) {
                    Text(ui.paymentMethodPayPalButtonLabel)
                }
                Button(
                    onClick = onGooglePlayClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(ui.paymentMethodGooglePlayButtonLabel)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(ui.paymentMethodCancelButtonLabel)
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
    val ui = com.google.ai.sample.util.TrialUiConfig.current()
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
                    text = ui.firstLaunchDialogTitle,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = ui.firstLaunchDialogBody,
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
                    Text(ui.firstLaunchDialogButton)
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
    val ui = com.google.ai.sample.util.TrialUiConfig.current()
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
                    text = ui.trialExpiredDialogTitle,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = ui.trialExpiredDialogBody,
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
                    Text(ui.trialExpiredDialogSubscribeButton)
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
    val ui = com.google.ai.sample.util.TrialUiConfig.current()
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
                    text = ui.infoDialogTitle,
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
                    Text(com.google.ai.sample.util.UiStringsConfig.get("apikey_dialog_ok", "OK"))
                }
            }
        }
    }
}
