package hu.krafcsikgergo.wakeonwan.services.receiver

import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager

interface SSHManager {
    suspend fun executeCommand(command: String): Boolean
    suspend fun testConnection(): Boolean
}

class SSHManagerImpl(
    private val dataStoreManager: DataStoreManager
) : SSHManager {

    private suspend fun getServerData(): ServerData {
        val serverData =
            dataStoreManager.getServerData() ?: throw IllegalStateException("Server data not found")
        return serverData
    }

    override suspend fun executeCommand(command: String): Boolean {
        val serverData = getServerData()

        val jsch = JSch()

        try {
            // Create SSH session
            val session =
                jsch.getSession(serverData.username, serverData.ipAddress, serverData.sshPort)
            session.setPassword(serverData.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect()

            // Execute the command
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.connect()

            // Wait for the command to complete
            while (!channel.isEOF) {
                // You can handle the command output here if needed
            }

            // Disconnect the SSH session
            channel.disconnect()
            session.disconnect()
            return true

        } catch (e: Exception) {
            Log.d("SSHManager", "Error: ${e.message}")
            return false
        }

    }

    override suspend fun testConnection(): Boolean {
        return try {
            val serverData = getServerData()
            val jsch = JSch()

            // Create SSH session
            val session = jsch.getSession(serverData.username, serverData.ipAddress, serverData.sshPort)
            session.setPassword(serverData.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect()

            // Test successful, disconnect immediately
            session.disconnect()
            Log.d("SSHManager", "SSH connection test successful")
            true

        } catch (e: Exception) {
            Log.d("SSHManager", "SSH connection test failed: ${e.message}")
            false
        }
    }
}