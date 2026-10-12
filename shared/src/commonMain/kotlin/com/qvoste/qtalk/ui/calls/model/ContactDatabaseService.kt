package com.qvoste.qtalk.ui.calls.model

data class ContactDatabaseState(
    val sourceUrl: String = "",
    val syncing: Boolean = false,
    val message: String = "Google Таблица не подключена",
    val lastSyncSuccessful: Boolean? = null
)

interface ContactDatabaseService {
    fun loadSourceUrl(): String
    fun saveSourceUrl(url: String)
    suspend fun downloadContacts(url: String): List<Contact>
}

object DisabledContactDatabaseService : ContactDatabaseService {
    override fun loadSourceUrl(): String = ""
    override fun saveSourceUrl(url: String) = Unit
    override suspend fun downloadContacts(url: String): List<Contact> =
        error("Синхронизация Google Таблиц недоступна на этой платформе")
}
