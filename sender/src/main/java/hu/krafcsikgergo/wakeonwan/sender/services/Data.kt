package hu.krafcsikgergo.wakeonwan.sender.services

import hu.krafcsikgergo.wakeonwan.common.model.defaultKtorPort
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
