package com.qvoste.qtalk.voip

enum class CallState {
    IDLE, INCOMING, DIALING, RINGING, ACTIVE, ENDING, ENDED, FAILED;

    val isInProgress: Boolean
        get() = this == INCOMING || this == DIALING || this == RINGING ||
            this == ACTIVE || this == ENDING
}

data class CallStatus(
    val state: CallState = CallState.IDLE,
    val number: String = "",
    val message: String = "",
    val startedAtEpochMillis: Long = 0L,
    val connectedAtEpochMillis: Long = 0L
)
