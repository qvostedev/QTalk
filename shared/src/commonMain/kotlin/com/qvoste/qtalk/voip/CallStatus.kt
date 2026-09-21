package com.qvoste.qtalk.voip

enum class CallState {
    IDLE, DIALING, RINGING, ACTIVE, ENDING, ENDED, FAILED;

    val isInProgress: Boolean
        get() = this == DIALING || this == RINGING || this == ACTIVE || this == ENDING
}

data class CallStatus(
    val state: CallState = CallState.IDLE,
    val number: String = "",
    val message: String = ""
)
