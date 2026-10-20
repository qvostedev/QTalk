package com.qvoste.qtalk.ui.calls.model

interface EmployeePresenceService {
    val enabled: Boolean
    fun loadEndpoint(): String = ""
    fun saveEndpoint(url: String) = Unit
    suspend fun publish(extension: String, status: EmployeeStatus)
    suspend fun load(): Map<String, EmployeeStatus>
}

object DisabledEmployeePresenceService : EmployeePresenceService {
    override val enabled: Boolean = false
    override suspend fun publish(extension: String, status: EmployeeStatus) = Unit
    override suspend fun load(): Map<String, EmployeeStatus> = emptyMap()
}
