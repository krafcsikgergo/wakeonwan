package hu.krafcsikgergo.wakeonwan.services.receiver

import kotlinx.serialization.Serializable

@Serializable
data class ServerData(
    // server location
    val ipAddress: String,
    val macAddress: String,

    // ssh access
    val sshPort: Int = 22,
    val username: String
)
