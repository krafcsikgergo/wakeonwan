package hu.krafcsikgergo.wakeonwan.common.model

import kotlinx.serialization.Serializable
import java.time.LocalTime

const val defaultKtorPort = 9753

@Serializable
data class Schedule(
    val id: Int = (0..Int.MAX_VALUE).random(),
    val time: Long, // seconds since midnight (0-86399)
    val turnOn: Boolean,
    val days: List<Boolean>, // Represents days from Monday to Sunday (index 0 = Monday)
    val enabled: Boolean = true // Controls whether the schedule is active
) {
    val timeInLocalTime: LocalTime
        get() = LocalTime.ofSecondOfDay(time)
}

@Serializable
data class StatusResponse(
    val message: String
)

/**
 * Payload encoded into the receiver's pairing QR code so a sender can add the
 * server and authenticate to it in a single scan.
 */
@Serializable
data class PairingPayload(
    val name: String,
    val ipAddress: String,
    val port: Int,
    val token: String
)
