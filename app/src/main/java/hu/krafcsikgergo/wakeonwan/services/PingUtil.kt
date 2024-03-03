package hu.krafcsikgergo.wakeonwan.services

import java.net.InetAddress

class PingUtil {
    companion object {
        suspend fun ping(host: String, timeout: Int = 1000): Boolean {
            return try {
                val inetAddress = InetAddress.getByName(host)
                inetAddress.isReachable(timeout)
            } catch (e: Exception) {
                false
            }
        }
    }
}