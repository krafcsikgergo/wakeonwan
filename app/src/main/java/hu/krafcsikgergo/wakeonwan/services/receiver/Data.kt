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
    val time: Long, // epoch millis
    val turnOn: Boolean,
    val days: List<Boolean> // Represents days from Monday to Sunday

    // getter for local time
) {
    val timeInLocalTime: LocalTime
        get() = LocalTime.ofSecondOfDay(time / 1000 % 86400)
}
