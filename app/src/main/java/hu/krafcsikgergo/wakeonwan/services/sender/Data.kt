package hu.krafcsikgergo.wakeonwan.services.sender

import hu.krafcsikgergo.wakeonwan.services.receiver.defaultKtorPort
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class KtorServerData @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val name: String,
    val ipAddress: String,
    val port: Int = defaultKtorPort
)

@Serializable
data class StatusResponse(
    val message: String
)