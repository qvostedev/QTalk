package com.qvoste.qtalk.ui.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.qvoste.qtalk.voip.RegistrationState

@Composable
fun CallsScreen(viewModel: CallsViewModel) {
    val registrationState by viewModel.registrationState.collectAsState()

    CallsContent(
        registrationState = registrationState,
        onConnectClick = viewModel::connect
    )
}
@Composable
private fun CallsContent(
    registrationState: RegistrationState,
    onConnectClick: () -> Unit
    ) {
    Column (
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "QTalk")
        Text(text = "SIP: $registrationState")

        Button(
            onClick = onConnectClick,
            enabled = registrationState == RegistrationState.DISCONNECTED
        ) {
            Text(text = "Connect")
        }
    }
}

@Preview
@Composable
private fun CallsContentPreview() {
    CallsContent(
        registrationState = RegistrationState.DISCONNECTED,
        onConnectClick = {}
    )
}
