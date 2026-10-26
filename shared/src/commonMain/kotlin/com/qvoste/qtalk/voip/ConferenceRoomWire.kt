package com.qvoste.qtalk.voip

internal sealed interface RoomMessage {
    val roomId: String
    data class Snapshot(override val roomId: String, val revision: Long, val host: String,
        val members: List<String>, val pending: List<String>, val muted: Set<String>, val speaking: Set<String>,
        val endpoint: String = "") : RoomMessage
    data class Microphone(override val roomId: String, val muted: Boolean) : RoomMessage
}

/** Room messages shared over SIP INFO, Google Sheets and LAN. */
internal object ConferenceRoomWire {
    const val subtype = "vnd.qtalk.room"
    private val number = Regex("\\+?[0-9]{1,20}")
    private val identifier = Regex("[a-fA-F0-9-]{36}")

    fun encode(message: RoomMessage): String = when (message) {
        is RoomMessage.Snapshot -> listOf("QTALK/1", "snapshot", message.roomId, message.revision.toString(),
            message.host, message.members.joinToString(","), message.pending.joinToString(","),
            message.muted.joinToString(","), message.speaking.joinToString(",")).let {
                (if (message.endpoint.isNotBlank()) it + message.endpoint else it).joinToString("\n")
            }
        is RoomMessage.Microphone -> listOf("QTALK/1", "microphone", message.roomId,
            if (message.muted) "1" else "0").joinToString("\n")
    }

    fun decode(body: String): RoomMessage? {
        if (body.length > 4096) return null
        val parts = body.split('\n')
        if (parts.size < 4 || parts[0] != "QTALK/1" || !identifier.matches(parts[2])) return null
        if (parts[1] == "microphone" && parts.size == 4 && parts[3] in setOf("0", "1")) {
            return RoomMessage.Microphone(parts[2], parts[3] == "1")
        }
        if (parts[1] != "snapshot" || parts.size !in 9..10) return null
        val endpoint = parts.getOrElse(9) { "" }
        if (endpoint.isNotEmpty() && !Regex("[0-9.]{7,15}:[0-9]{1,5}/[a-f0-9-]{36}").matches(endpoint)) return null
        val revision = parts[3].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        fun numbers(index: Int) = parts[index].split(',').filter { it.isNotBlank() }
        val members = numbers(5); val pending = numbers(6); val muted = numbers(7); val speaking = numbers(8)
        if (!number.matches(parts[4]) || members.isEmpty() || members.size > MAX_CONFERENCE_PARTICIPANTS ||
            (members + pending).distinct().size > MAX_CONFERENCE_PARTICIPANTS || members.distinct().size != members.size ||
            (members + pending + muted + speaking).any { !number.matches(it) } || parts[4] !in members ||
            muted.any { it !in members } || speaking.any { it !in members }) return null
        return RoomMessage.Snapshot(parts[2], revision, parts[4], members, pending, muted.toSet(), speaking.toSet(), endpoint)
    }
}
