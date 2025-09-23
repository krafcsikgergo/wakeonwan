package hu.krafcsikgergo.wakeonwan.services.receiver

import kotlinx.serialization.Serializable
import java.time.LocalTime
import kotlin.uuid.ExperimentalUuidApi

const val defaultKtorPort = 9753

@Serializable
data class ServerData(
    // server location
    val ipAddress: String,
    val macAddress: String,

    // ssh access
    val sshPort: Int = 22,
    val username: String,
    val password: String
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Schedule(
    val id: Int = (0..Int.MAX_VALUE).random(),
    val time: Long, // seconds since midnight (0-86399)
    val turnOn: Boolean,
    val days: List<Boolean> // Represents days from Monday to Sunday (index 0 = Monday)
) {
    val timeInLocalTime: LocalTime
        get() = LocalTime.ofSecondOfDay(time)
}
