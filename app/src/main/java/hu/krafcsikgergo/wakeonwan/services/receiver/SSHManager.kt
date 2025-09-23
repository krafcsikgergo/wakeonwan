package hu.krafcsikgergo.wakeonwan.services.receiver

import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

interface SSHManager {
    suspend fun executeCommand(serverData: ServerData, command: String): SshCommandResult
    suspend fun testConnection(serverData: ServerData): Boolean
}

data class SshCommandResult(
    val success: Boolean,
    val output: String? = null,
    val error: Exception? = null,
    val exitStatus: Int? = null
)

class SSHManagerImpl() : SSHManager {
    private val TAG = "SSHManager"

    private suspend fun sshConnect(
        serverData: ServerData
    ) = withContext(Dispatchers.IO) {
        val jsch = JSch()
        val session =
            jsch.getSession(serverData.username, serverData.ipAddress, serverData.sshPort)
        session.setPassword(serverData.password)
        session.setConfig("StrictHostKeyChecking", "no")
        
        // Add timeout settings for better reliability
        session.setTimeout(10000) // 10 seconds session timeout
        session.setConfig("ConnectTimeout", "5000") // 5 seconds connect timeout
        
        session.connect()
        session
    }

    override suspend fun executeCommand(serverData: ServerData, command: String): SshCommandResult {
        var session: com.jcraft.jsch.Session? = null
        var channel: ChannelExec? = null
        
        try {
            // Create SSH session
            session = sshConnect(serverData)

            // Execute the command
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            
            // Set up streams before connecting
            val outputStream = ByteArrayOutputStream()
            val errorStream = ByteArrayOutputStream()
            channel.setOutputStream(outputStream)
            channel.setErrStream(errorStream)
            
            // Connect with timeout
            channel.connect(5000) // 5 seconds channel timeout

            // Wait for channel to close with timeout
            while (!channel.isClosed) {
                Thread.sleep(100)
            }
            
            val output = outputStream.toString()
            val errorOutput = errorStream.toString()
            val exitStatus = channel.exitStatus
            
            Log.d(TAG, "Command executed with exit status: $exitStatus")
            if (errorOutput.isNotEmpty()) {
                Log.d(TAG, "Error output: $errorOutput")
            }
            
            return SshCommandResult(
                success = exitStatus == 0,
                output = output.ifEmpty { errorOutput },
                exitStatus = exitStatus
            )

        } catch (e: JSchException) {
            Log.e(TAG, "JSch connection error: ${e.message}", e)
            return SshCommandResult(false, error = e, exitStatus = -1)
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "SSH connection timeout: ${e.message}", e)
            return SshCommandResult(false, error = e, exitStatus = -1)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected SSH error: ${e.message}", e)
            return SshCommandResult(false, error = e, exitStatus = -1)
        } finally {
            // Always disconnect resources
            try {
                channel?.disconnect()
                session?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error while disconnecting SSH resources: ${e.message}")
            }
        }
    }

    override suspend fun testConnection(serverData: ServerData): Boolean {
        var session: com.jcraft.jsch.Session? = null
        
        return try {
            // Create SSH session
            session = sshConnect(serverData)
            Log.d(TAG, "SSH connection test successful")
            true

        } catch (e: JSchException) {
            Log.e(TAG, "SSH test connection failed: ${e.message}", e)
            false
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "SSH test connection timeout: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during SSH test: ${e.message}", e)
            false
        } finally {
            // Always disconnect session
            try {
                session?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error while disconnecting SSH test session: ${e.message}")
            }
        }
    }
}