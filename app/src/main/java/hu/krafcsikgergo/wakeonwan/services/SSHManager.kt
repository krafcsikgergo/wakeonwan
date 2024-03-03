package hu.krafcsikgergo.wakeonwan.services

import android.content.Context
import android.util.Log
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import io.ktor.utils.io.errors.IOException
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader


class SSHManager(
    private val username: String,
    private val ipAddress: String,
    private val port: Int?,
    private val password: String
) {

    fun executeCommand(command: String): Boolean {
        val jsch = JSch()

        try {
            // Create SSH session
            val session = jsch.getSession(username, ipAddress, port ?: 22)
            session.setPassword(password)
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
}