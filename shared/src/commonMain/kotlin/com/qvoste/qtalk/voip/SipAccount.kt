package com.qvoste.qtalk.voip

enum class SipTransport(val uriValue: String) {
    UDP("udp"),
    TCP("tcp"),
    TLS("tls")
}

data class SipAccount(
    val username: String,
    val password: String? = null,
    val ha1: String? = null,
    val realm: String? = null,
    val domain: String,
    val port: Int = 5060,
    val transport: SipTransport = SipTransport.UDP,
    val registrationExpires: Int = 3600
)
