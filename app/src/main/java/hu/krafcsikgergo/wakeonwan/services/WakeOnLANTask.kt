package hu.krafcsikgergo.wakeonwan.services

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress


fun sendWakeOnLANPacket(ipAddress: String, macAddress: String) {
    try {

        var delimiter = ":"
        if (!macAddress.contains(":")){
            delimiter = "-"
        }

        // Convert the MAC address to bytes
        val macBytes = macAddress.split(delimiter).map { it.toInt(16).toByte() }.toByteArray()

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
        // Or use 255.255.255.255 instead of parameter to broadcast it
        val broadcastAddress = InetAddress.getByName("255.255.255.255")
        val packet = DatagramPacket(magicPacket, magicPacket.size, broadcastAddress, 9)

        // Create a DatagramSocket and send the packet
        val socket = DatagramSocket()
        socket.send(packet)

        // Close the socket
        socket.close()

    } catch (e: Exception) {
        e.printStackTrace()
    }
}
