package com.qvoste.qtalk.voip

import org.linphone.core.*

/** Explicit unregistered local/peer accounts on the same Core; preserves the registered default. */
internal class LanHandoffSip(private val core: Core, private val factory: Factory) {
    private val accounts = linkedMapOf<String, Account>()
    private val identities = mutableMapOf<Account, LanSipEndpoint>()

    fun releaseUnused() {
        check(core.calls.isEmpty())
        accounts.values.forEach(core::removeAccount)
        accounts.clear(); identities.clear()
    }

    fun endpoint(host: String): LanSipEndpoint {
        if (core.transportsUsed.udpPort <= 0) {
            val used = core.transportsUsed
            val transports = core.transports
            // Preserve actual bound TCP/TLS ports, including accounts using random ports.
            if (used.tcpPort > 0) transports.tcpPort = used.tcpPort
            if (used.tlsPort > 0) transports.tlsPort = used.tlsPort
            transports.udpPort = -1
            check(core.setTransports(transports) == 0) { "LAN transport unavailable" }
        }
        return checkNotNull(LanSipEndpoint.parse("$host:${core.transportsUsed.udpPort}")) { "LAN endpoint unavailable" }
    }

    fun account(number: String, endpoint: LanSipEndpoint, destination: LanSipEndpoint = endpoint): Account {
        require(number.matches(Regex("[0-9]{1,20}")))
        val uri = "sip:$number@$endpoint;transport=udp"
        val key = "$uri|$destination"
        accounts[key]?.let { return it }
        check(accounts.size < ConferenceHandoffLimits.MAX_CACHED_LAN_ACCOUNTS) { "LAN account capacity reached" }
        val savedDefault = core.defaultAccount
        val params = core.createAccountParams().apply {
            identityAddress = factory.createAddress(uri)
            // liblinphone uses the account server as next hop even without routes.
            // A separate unregistered LAN account pins each invitation to its negotiated
            // peer, while the local mixer keeps its own account. Never reuse the registrar.
            serverAddress = factory.createAddress("sip:$destination;transport=udp")
            customContact = factory.createAddress(uri)
            isRegisterEnabled = false
            isOutboundProxyEnabled = false
            check(setRoutesAddresses(emptyArray()) == 0)
            conferenceFactoryAddress = null
            natPolicy = core.createNatPolicy().apply {
                isIceEnabled = false; isStunEnabled = false; isTurnEnabled = false; isUpnpEnabled = false
            }
        }
        val created = core.createAccount(params)
        check(core.addAccount(created) == 0)
        core.defaultAccount = savedDefault
        accounts[key] = created; identities[created] = endpoint
        return created
    }

    fun mixer(account: Account): Conference = checkNotNull(core.createConferenceWithParams(
        core.createConferenceParams(null).apply {
            isAudioEnabled = true; isVideoEnabled = false; isLocalParticipantEnabled = true
            isOneParticipantConferenceEnabled = true; this.account = account; conferenceFactoryAddress = null
        }))

    companion object {
        fun ready(staged: List<Call>, expected: Int, mixer: Conference?, requiresMixer: Boolean): Boolean =
            staged.size == expected && staged.all { call ->
                call.state == Call.State.StreamsRunning && call.audioStats?.let {
                    it.rtpPacketRecv > 0 && it.rtpPacketSent > 0
                } == true
            } &&
                (!requiresMixer || mixer?.isIn == true && staged.all { it.conference == mixer })
    }

    fun params(account: Account, call: Call? = null): CallParams = checkNotNull(core.createCallParams(call)).apply {
        this.account = account; isVideoEnabled = false
    }

    fun invite(mixer: Conference, account: Account, number: String, endpoint: LanSipEndpoint, id: String): Call {
        require(number.matches(Regex("[0-9]{1,20}")))
        check(!account.params.isRegisterEnabled && account.params.routesAddresses.isEmpty() &&
            !account.params.isOutboundProxyEnabled) { "LAN account must have no proxy" }
        val address = checkNotNull(factory.createAddress("sip:$number@$endpoint;transport=udp"))
        val local = checkNotNull(account.params.identityAddress)
        val identityEndpoint = checkNotNull(identities[account])
        val directAccount = account(number = checkNotNull(local.username), endpoint = identityEndpoint, destination = endpoint)
        val previous = core.calls.toSet()
        mixer.inviteParticipants(arrayOf(address), params(directAccount).apply { addCustomHeader("X-QTalk-Handoff", id) })
        return checkNotNull(core.calls.firstOrNull { it !in previous && it.remoteAddress?.username == number })
    }
}
