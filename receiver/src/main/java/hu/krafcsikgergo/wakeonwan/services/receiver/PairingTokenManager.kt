package hu.krafcsikgergo.wakeonwan.services.receiver

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Collections

/**
 * Generates and stores the shared secret a sender device must present (via the
 * X-WOL-Token header) to use the receiver's wake/shutdown/schedule endpoints.
 * The token is handed out once, encoded into the pairing QR code.
 */
interface PairingTokenManager {
    fun hasToken(): Boolean
    fun getToken(): String?
    fun generateToken(): String

    /**
     * Best-effort discovery of this device's own IPv4 address for the QR payload.
     * Prefers the Tailscale address (100.64.0.0/10) so pairing keeps working from
     * any network; falls back to the first non-loopback IPv4 address otherwise.
     */
    fun getLocalIpAddress(): String?
}

class PairingTokenManagerImpl(
    private val context: Context
) : PairingTokenManager {

    private val encryptedPrefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun hasToken(): Boolean = encryptedPrefs.contains(KEY_TOKEN)

    override fun getToken(): String? = encryptedPrefs.getString(KEY_TOKEN, null)

    override fun generateToken(): String {
        val bytes = ByteArray(32) // 256 bits
        SecureRandom().nextBytes(bytes)
        val token = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)

        encryptedPrefs.edit()
            .putString(KEY_TOKEN, token)
            .apply()

        return token
    }

    override fun getLocalIpAddress(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val addresses = interfaces
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }

            // Prefer the Tailscale address (always in its 100.64.0.0/10 CGNAT range,
            // regardless of what Android names the VPN tunnel interface) so the QR
            // code keeps working from any network, not just the local LAN.
            addresses.firstOrNull { isTailscaleAddress(it) }?.hostAddress
                ?: addresses.firstOrNull()?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    private fun isTailscaleAddress(address: Inet4Address): Boolean {
        val bytes = address.address
        val firstOctet = bytes[0].toInt() and 0xFF
        val secondOctet = bytes[1].toInt() and 0xFF
        return firstOctet == 100 && secondOctet in 64..127
    }

    companion object {
        private const val PREFS_NAME = "pairing_token_store"
        private const val KEY_TOKEN = "pairing_token"
    }
}
