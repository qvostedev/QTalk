package com.qvoste.qtalk.ui.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.qvoste.qtalk.voip.CallState
import com.qvoste.qtalk.voip.CallStatus
import com.qvoste.qtalk.voip.RegistrationState

@Composable
fun CallsScreen(viewModel: CallsViewModel) {
    val registration by viewModel.registrationState.collectAsState()
    val call by viewModel.callStatus.collectAsState()
    val error by viewModel.error.collectAsState()
    CallsContent(registration, call, error, viewModel::connect, viewModel::call, viewModel::hangUp)
}

@Composable
private fun CallsContent(
    registration: RegistrationState,
    call: CallStatus,
    error: String?,
    onConnect: () -> Unit,
    onCall: (String) -> Unit,
    onHangUp: () -> Unit
) {
    var number by rememberSaveable { mutableStateOf("600") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("QTalk", style = MaterialTheme.typography.headlineMedium)
        Text("SIP: $registration")
        Button(onClick = onConnect, enabled = !call.state.isInProgress &&
            (registration == RegistrationState.DISCONNECTED || registration == RegistrationState.FAILED)) {
            Text("Connect")
        }
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            label = { Text("Внутренний номер") },
            singleLine = true,
            enabled = !call.state.isInProgress
        )
        Text("600 — эхо-тест: скажите что-нибудь в микрофон")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onCall(number) }, enabled = registration == RegistrationState.REGISTERED &&
                !call.state.isInProgress && number.trim().matches(Regex("[0-9]{1,20}"))) {
                Text("Позвонить")
            }
            Button(onClick = onHangUp, enabled = call.state.isInProgress && call.state != CallState.ENDING) {
                Text("Завершить")
            }
        }
        Text(when (call.state) {
            CallState.IDLE -> "Готов к звонку"
            CallState.DIALING -> "Набор ${call.number}…"
            CallState.RINGING -> "Ожидание ответа ${call.number}…"
            CallState.ACTIVE -> "Разговор с ${call.number}"
            CallState.ENDING -> "Завершение звонка…"
            CallState.ENDED -> "Звонок завершён"
            CallState.FAILED -> "Звонок не удался: ${call.message}"
        })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Preview
@Composable
private fun CallsContentPreview() {
    MaterialTheme {
        CallsContent(RegistrationState.REGISTERED, CallStatus(), null, {}, {}, {})
    }
}
