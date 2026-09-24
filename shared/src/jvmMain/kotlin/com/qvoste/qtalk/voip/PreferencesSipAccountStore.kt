package com.qvoste.qtalk.voip

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.prefs.Preferences

class PreferencesSipAccountStore : SipAccountStore {
    private val preferences = Preferences.userRoot().node("com/qvoste/qtalk/sip")

    override fun load(): SipAccount? {
        val username = preferences.get("username", "").trim()
        val domain = preferences.get("domain", "").trim()
        val ha1 = preferences.get("ha1", "").trim()
        if (username.isBlank() || domain.isBlank() || ha1.isBlank()) return null

        return SipAccount(
            username = username,
            ha1 = ha1,
            realm = preferences.get("realm", "").ifBlank { null },
            domain = domain,
            port = preferences.getInt("port", 5060),
            transport = runCatching {
                SipTransport.valueOf(preferences.get("transport", SipTransport.UDP.name))
            }.getOrDefault(SipTransport.UDP),
            registrationExpires = preferences.getInt("registrationExpires", 3600)
        )
    }

    override fun save(account: SipAccount) {
        val realm = account.realm.orEmpty()
        val storedHa1 = account.ha1?.takeIf { it.isNotBlank() }
            ?: account.password?.takeIf { it.isNotBlank() }?.let {
                md5("${account.username}:$realm:$it")
            }
            ?: error("Не удалось сохранить данные авторизации")

        preferences.put("username", account.username)
        preferences.put("ha1", storedHa1)
        preferences.put("realm", realm)
        preferences.put("domain", account.domain)
        preferences.putInt("port", account.port)
        preferences.put("transport", account.transport.name)
        preferences.putInt("registrationExpires", account.registrationExpires)
        preferences.flush()
    }

    override fun clear() {
        preferences.clear()
        preferences.flush()
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
