package com.qvoste.qtalk.domain.calls

import com.qvoste.qtalk.voip.SipAccount
import com.qvoste.qtalk.voip.VoipEngine

class ConnectSipUseCase(private val voipEngine : VoipEngine) {
    suspend operator fun invoke(account: SipAccount) {
        voipEngine.register(account)
    }
}