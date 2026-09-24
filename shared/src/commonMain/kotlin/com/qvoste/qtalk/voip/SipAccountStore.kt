package com.qvoste.qtalk.voip

interface SipAccountStore {
    fun load(): SipAccount?
    fun save(account: SipAccount)
    fun clear()
}

class InMemorySipAccountStore : SipAccountStore {
    private var account: SipAccount? = null

    override fun load(): SipAccount? = account
    override fun save(account: SipAccount) {
        this.account = account
    }
    override fun clear() {
        account = null
    }
}
