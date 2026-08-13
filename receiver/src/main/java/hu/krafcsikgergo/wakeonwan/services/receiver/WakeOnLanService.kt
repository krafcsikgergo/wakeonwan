package hu.krafcsikgergo.wakeonwan.services.receiver

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import hu.krafcsikgergo.wakeonwan.common.services.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

interface WakeOnLanService {
    var serverStarted: Long?

    /**
     * Starts the Ktor server service.
     * @return true if service started successfully, false otherwise
     */
    suspend fun startKtorServer(): Boolean

    /**
     * Stops the Ktor server service.
     * @return true if service stopped successfully, false otherwise
     */
    suspend fun stopKtorServer(): Boolean

    /**
     * Checks if the Ktor server service is currently running.
     * @return true if service is running, false otherwise
     */
    suspend fun isKtorServerRunning(): Boolean

    /**
     * Sends a Wake-on-LAN magic packet to the specified MAC address.
     */
    suspend fun sendWakeOnLanPacket(serverData: ServerData): Result<String>

    /**
     * Executes SSH shutdown command on the target server.
     */
    suspend fun executeShutdownCommand(serverData: ServerData): Result<String>

    /**
     * Checks connectivity to the target server: first via ping, then (only if the ping
     * succeeds) via a plain SSH connect/disconnect.
     */
    suspend fun checkConnection(serverData: ServerData): ConnectionCheckResult
}

data class ConnectionCheckResult(
    val pingSuccess: Boolean,
    val sshSuccess: Boolean,
    val message: String
)

/**
 * Implementation of WakeOnLanService that manages Ktor server service
 * and handles Wake-on-LAN and SSH operations.
 */
class WakeOnLanServiceImpl(
    private val context: Context,
    private val sshManager: SSHManager,
    private val logManager: LogManager
) : WakeOnLanService {

    private val TAG = "WakeOnLanService"

    override var serverStarted: Long? = null

    override suspend fun startKtorServer(): Boolean {
        if (isKtorServerRunning()) {
            logManager.d(TAG, "Ktor server is already running.")
            // If server is already running but we don't have a start time, set it now
            if (serverStarted == null) {
                serverStarted = System.currentTimeMillis()
                logManager.d(TAG, "Set server start time for existing running server: $serverStarted")
            }
            return true
        }

        return try {
            withContext(Dispatchers.Main) {
                val intent = Intent(context, KtorServerService::class.java)
                val result = context.startService(intent)
                if (result != null) {
                    serverStarted = System.currentTimeMillis()
                }
                result != null
            }.also { success ->
                logManager.d(TAG, "Ktor server start attempt: ${if (success) "success" else "failed"}")
            }
        } catch (e: Exception) {
            logManager.e(TAG, "Error starting Ktor server: ${e.message}", e)
            false
        }
    }

    override suspend fun stopKtorServer(): Boolean {
        return try {
            withContext(Dispatchers.Main) {
                val intent = Intent(context, KtorServerService::class.java)
                context.stopService(intent)
                serverStarted = null
                true
            }.also {
                logManager.d(TAG, "Ktor server stop attempt: success")
            }
        } catch (e: Exception) {
            logManager.e(TAG, "Error stopping Ktor server: ${e.message}", e)
            false
        }
    }

    override suspend fun isKtorServerRunning(): Boolean {
        return try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)

            val isRunning = runningServices.any { serviceInfo ->
                serviceInfo.service.className == KtorServerService::class.java.name
            }

            logManager.d(TAG, "Ktor server running status: $isRunning")
            isRunning
        } catch (e: Exception) {
            logManager.e(TAG, "Error checking Ktor server status: ${e.message}", e)
            false
        }
    }

    override suspend fun sendWakeOnLanPacket(serverData: ServerData): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                var delimiter = ":"
                if (!serverData.macAddress.contains(":")) {
                    delimiter = "-"
                }

                // Convert the MAC address to bytes
                val macBytes = serverData.macAddress.split(delimiter).map { it.toInt(16).toByte() }.toByteArray()

                // Create a byte array for the magic packet
                val magicPacket = ByteArray(6 + 16 * macBytes.size)
                // Fill the first 6 bytes with 0xFF
                for (i in 0 until 6) {
                    magicPacket[i] = 0xFF.toByte()
                }
                // Repeat the MAC address 16 times
                for (i in 6 until magicPacket.size) {
                    magicPacket[i] = macBytes[i % 6]
                }

                // Create a DatagramPacket with the magic packet and broadcast address
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(magicPacket, magicPacket.size, broadcastAddress, 9)

                // Create a DatagramSocket and send the packet
                val socket = DatagramSocket()
                socket.send(packet)
                socket.close()

                val message = "Wake-on-LAN packet sent to ${serverData.macAddress} via broadcast"
                logManager.d(TAG, message)
                Result.success(message)
            } catch (e: Exception) {
                val errorMessage = "Failed to send Wake-on-LAN packet: ${e.message}"
                logManager.e(TAG, errorMessage, e)
                Result.failure(Exception(errorMessage, e))
            }
        }
    }

    override suspend fun executeShutdownCommand(serverData: ServerData): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val sshResult = sshManager.executeCommand(serverData, "sudo shutdown now")

                if (!sshResult.success) {
                    throw sshResult.error
                        ?: Exception("SSH command failed with exit status ${sshResult.exitStatus}")
                }

                val message = "Shutdown command executed successfully on ${serverData.ipAddress}"
                logManager.d(TAG, message)
                Result.success(message)
            } catch (e: Exception) {
                val errorMessage = "SSH connection failed to ${serverData.ipAddress}: ${e.message}"
                logManager.e(TAG, errorMessage, e)
                Result.failure(Exception(errorMessage, e))
            }
        }
    }

    override suspend fun checkConnection(serverData: ServerData): ConnectionCheckResult {
        return withContext(Dispatchers.IO) {
            val pingSuccess = try {
                InetAddress.getByName(serverData.ipAddress).isReachable(1000)
            } catch (e: Exception) {
                logManager.e(TAG, "Ping failed: ${e.message}", e)
                false
            }

            logManager.d(TAG, "Connection check - ping: $pingSuccess")

            if (!pingSuccess) {
                return@withContext ConnectionCheckResult(
                    pingSuccess = false,
                    sshSuccess = false,
                    message = "Host is not reachable via ping, SSH test skipped"
                )
            }

            val sshSuccess = sshManager.testConnection(serverData)
            logManager.d(TAG, "Connection check - ssh: $sshSuccess")

            ConnectionCheckResult(
                pingSuccess = true,
                sshSuccess = sshSuccess,
                message = if (sshSuccess) {
                    "Host is fully reachable (ping and SSH)"
                } else {
                    "Host is reachable via ping but SSH connection failed"
                }
            )
        }
    }
}
