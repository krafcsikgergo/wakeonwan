package hu.krafcsikgergo.wakeonwan.services

import java.time.LocalTime

data class Schedule(
    val time: LocalTime,
    val turnOn: Boolean,
    val days: List<Boolean> // Represents days from Monday to Sunday
)

object ServerData {
    var username: String = ""
    var ipAddress: String = "192.168.0.1"
    var macAddress: String = "00:00:00:00:00:00"
    var sshPort: Int = 22
    var password: String = ""
    val schedules: MutableList<Schedule> = mutableListOf()
}