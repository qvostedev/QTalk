package com.qvoste.qtalk.voip

/** Numeric IPv4 only: no DNS, public, link-local, multicast, or bearer URLs. */
internal data class LanSipEndpoint private constructor(val host: String, val port: Int) {
    override fun toString() = "$host:$port"
    companion object {
        fun parse(text: String, allowLoopbackForTest: Boolean = false): LanSipEndpoint? {
            val parts = text.split(':')
            if (parts.size != 2) return null
            val octets = parts[0].split('.')
            if (octets.size != 4 || octets.any { it.isEmpty() || it.length > 3 ||
                    it.any { c -> c !in '0'..'9' } || it.length > 1 && it[0] == '0' }) return null
            val bytes = octets.map { it.toIntOrNull() ?: return null }
            if (bytes.any { it !in 0..255 }) return null
            val private = bytes[0] == 10 || bytes[0] == 172 && bytes[1] in 16..31 || bytes[0] == 192 && bytes[1] == 168
            if (!private && !(allowLoopbackForTest && bytes[0] == 127)) return null
            if (parts[1].isEmpty() || parts[1].length > 5 || parts[1].any { it !in '0'..'9' }) return null
            val port = parts[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            return LanSipEndpoint(parts[0], port)
        }
    }
}
