package com.qvoste.qtalk.voip

internal data class ConferenceHandoffPermit(val id: String, val successor: String, val roomId: String, val expiresAt: Long) {
    fun accepts(remote: String, headerId: String?, currentRoom: String, now: Long, hasStagedCall: Boolean): Boolean =
        remote == successor && headerId == id && currentRoom == roomId && now < expiresAt && !hasStagedCall
}

internal class ConferenceHandoff(val id: String, val successor: String, val members: List<String>) {
    enum class Phase { PREPARING, CONNECTING, COMMITTING, COMPLETE, ABORTED }
    var phase = Phase.PREPARING
        private set
    var rollbackAcknowledged = false
        private set
    interface Transport {
        fun instruct(instruction: String)
        suspend fun awaitAcknowledgements(ack: String, timeoutMillis: Long): Boolean
    }
    private val command get() = "$id|$successor|${members.joinToString(",")}"

    suspend fun transfer(transport: Transport) {
        if (phase == Phase.COMPLETE) return
        check(phase != Phase.ABORTED) { "Передача отменена. Повторите выход" }
        if (phase != Phase.COMMITTING) {
            try {
                transport.instruct("prepare|$command")
                check(transport.awaitAcknowledgements("prepared:$id", ConferenceHandoffTiming.PREPARE_TIMEOUT_MILLIS)) {
                    "Участники не подтвердили подготовку. Исходный звонок сохранён"
                }
                phase = Phase.CONNECTING
                transport.instruct("connect|$command")
                check(transport.awaitAcknowledgements("ready:$id", ConferenceHandoffTiming.READY_TIMEOUT_MILLIS)) {
                    "Новые аудиосоединения не готовы. Передача отменена, исходные соединения сохранены"
                }
            } catch (error: Throwable) {
                phase = Phase.ABORTED
                transport.instruct("abort|$command")
                rollbackAcknowledged = transport.awaitAcknowledgements("aborted:$id", ConferenceHandoffTiming.ROLLBACK_TIMEOUT_MILLIS)
                throw error
            }
            // This decision is irreversible. A lost commit acknowledgement is retried,
            // never presented as a rollback or permission to tear down the old mixer.
            phase = Phase.COMMITTING
        }
        transport.instruct("commit|$command")
        check(transport.awaitAcknowledgements("committed:$id", ConferenceHandoffTiming.COMMIT_TIMEOUT_MILLIS)) {
            "Передача завершается. Оставьте QTalk открытым и повторите выход для подтверждения"
        }
        phase = Phase.COMPLETE
    }
}
