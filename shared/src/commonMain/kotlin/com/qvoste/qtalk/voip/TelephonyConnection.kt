package com.qvoste.qtalk.voip

enum class TelephonyConnection {
    DISCONNECTED, CONNECTING, CONNECTED, SERVER_UNREACHABLE, NETWORK_UNAVAILABLE,
    AUTHENTICATION_FAILED, REGISTRATION_REJECTED;

    val showWarning: Boolean get() = this !in setOf(DISCONNECTED, CONNECTED)
    val retryable: Boolean get() = this == SERVER_UNREACHABLE || this == NETWORK_UNAVAILABLE
}

internal fun registrationDuringRefresh(previous: RegistrationState): RegistrationState =
    if (previous == RegistrationState.REGISTERED) previous else RegistrationState.CONNECTING

internal fun classifyRegistrationFailure(protocolCode: Int, authenticationFailure: Boolean): TelephonyConnection = when {
    authenticationFailure || protocolCode in setOf(401, 403, 407) -> TelephonyConnection.AUTHENTICATION_FAILED
    protocolCode == 0 || protocolCode in setOf(408, 480, 500, 502, 503, 504) -> TelephonyConnection.SERVER_UNREACHABLE
    else -> TelephonyConnection.REGISTRATION_REJECTED
}

internal fun reconnectDelayMillis(attempt: Int): Long =
    (5_000L * (1L shl attempt.coerceIn(0, 4))).coerceAtMost(60_000L)
