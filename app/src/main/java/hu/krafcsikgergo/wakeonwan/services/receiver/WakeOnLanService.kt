package hu.krafcsikgergo.wakeonwan.services.receiver

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import hu.krafcsikgergo.wakeonwan.services.receiver.KtorServerService
import hu.krafcsikgergo.wakeonwan.services.sendWakeOnLANPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service interface for Wake-on-LAN and SSH operations.
 * Handles the actual server service lifecycle and low-level network operations.
 */
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
     * @param macAddress The MAC address to wake up
     * @param ipAddress The broadcast IP address to use
     * @return Result indicating success or failure with message
     */
    suspend fun sendWakeOnLanPacket(macAddress: String, ipAddress: String): Result<String>

    /**
     * Executes SSH shutdown command on the target server.
     * @param ipAddress The server IP address
     * @param username The SSH username
     * @param password The SSH password
     * @param port The SSH port (default 22)
     * @param shutdownCommand The shutdown command to execute
     * @return Result indicating success or failure with message
     */
    suspend fun executeShutdownCommand(
        ipAddress: String,
        username: String,
        password: String,
        port: Int = 22,
        shutdownCommand: String = "sudo shutdown now"
    ): Result<String>

    /**
     * Gets the current status of the WOL service.
     * @return Service status information
     */
    suspend fun getServiceStatus(): WakeOnLanServiceStatus
}

/**
 * Status information for the Wake-on-LAN service.
 */
data class WakeOnLanServiceStatus(
    val isKtorServerRunning: Boolean = false,
    val serverIpAddress: String = "",
    val serverPort: Int = 0,
    val lastWakeAttempt: Long? = null,
    val lastShutdownAttempt: Long? = null
)

/**
 * Implementation of WakeOnLanService that manages Ktor server service
 * and handles Wake-on-LAN and SSH operations.
 */
class WakeOnLanServiceImpl(
    private val context: Context,
    private val sshManager: SSHManager
) : WakeOnLanService {

    private val TAG = "WakeOnLanService"
    
    override var serverStarted: Long? = null

    override suspend fun startKtorServer(): Boolean {
        if (isKtorServerRunning()) {
            Log.d(TAG, "Ktor server is already running.")
            // If server is already running but we don't have a start time, set it now
            if (serverStarted == null) {
                serverStarted = System.currentTimeMillis()
                Log.d(TAG, "Set server start time for existing running server: $serverStarted")
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
                Log.d(TAG, "Ktor server start attempt: ${if (success) "success" else "failed"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Ktor server: ${e.message}", e)
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
                Log.d(TAG, "Ktor server stop attempt: success")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Ktor server: ${e.message}", e)
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

            Log.d(TAG, "Ktor server running status: $isRunning")
            isRunning
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Ktor server status: ${e.message}", e)
            false
        }
    }

    override suspend fun sendWakeOnLanPacket(
        macAddress: String,
        ipAddress: String
    ): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                sendWakeOnLANPacket(ipAddress, macAddress)
                val message = "Wake-on-LAN packet sent to $macAddress via $ipAddress"
                Log.d(TAG, message)
                Result.success(message)
            }
        } catch (e: Exception) {
            val errorMessage = "Failed to send Wake-on-LAN packet: ${e.message}"
            Log.e(TAG, errorMessage, e)
            Result.failure(Exception(errorMessage, e))
        }
    }

    override suspend fun executeShutdownCommand(
        ipAddress: String,
        username: String,
        password: String,
        port: Int,
        shutdownCommand: String
    ): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                val success = sshManager.executeCommand(shutdownCommand)

                if (success) {
                    val message = "Shutdown command executed successfully on $ipAddress"
                    Log.d(TAG, message)
                    Result.success(message)
                } else {
                    val errorMessage = "Failed to execute shutdown command on $ipAddress"
                    Log.e(TAG, errorMessage)
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            val errorMessage = "SSH connection failed to $ipAddress: ${e.message}"
            Log.e(TAG, errorMessage, e)
            Result.failure(Exception(errorMessage, e))
        }
    }

    override suspend fun getServiceStatus(): WakeOnLanServiceStatus {
        return try {
            WakeOnLanServiceStatus(
                isKtorServerRunning = isKtorServerRunning(),
                serverIpAddress = "", // TODO: Get from configuration when available
                serverPort = 0, // TODO: Get from configuration when available
                lastWakeAttempt = null, // TODO: Implement when needed
                lastShutdownAttempt = null // TODO: Implement when needed
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting service status: ${e.message}", e)
            WakeOnLanServiceStatus() // Return default status
        }
    }
}