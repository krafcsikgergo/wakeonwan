package hu.krafcsikgergo.wakeonwan.services.receiver

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream

/**
 * Generates and stores the RSA key pair the receiver uses to authenticate to the
 * target server over SSH, so the server no longer needs to trust it by IP address.
 * The private key is kept in an encrypted, app-private store; only the public key
 * ever needs to leave the device (copied into the server's authorized_keys).
 */
interface SshKeyManager {
    fun hasKeyPair(): Boolean
    fun getPublicKey(): String?
    fun getPrivateKeyBytes(): ByteArray?
    fun getPublicKeyBytes(): ByteArray?
    fun generateKeyPair(): String
    fun clearKeyPair()
}

class SshKeyManagerImpl(
    private val context: Context
) : SshKeyManager {

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

    override fun hasKeyPair(): Boolean = encryptedPrefs.contains(KEY_PRIVATE)

    override fun getPublicKey(): String? = encryptedPrefs.getString(KEY_PUBLIC, null)

    override fun getPrivateKeyBytes(): ByteArray? = encryptedPrefs.getString(KEY_PRIVATE, null)?.toByteArray()

    override fun getPublicKeyBytes(): ByteArray? = encryptedPrefs.getString(KEY_PUBLIC, null)?.toByteArray()

    override fun generateKeyPair(): String {
        val jsch = JSch()
        val keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 3072)

        val privateKeyOut = ByteArrayOutputStream()
        val publicKeyOut = ByteArrayOutputStream()
        keyPair.writePrivateKey(privateKeyOut)
        keyPair.writePublicKey(publicKeyOut, "wakeonwan-receiver")
        keyPair.dispose()

        val privateKey = privateKeyOut.toString(Charsets.UTF_8.name())
        val publicKey = publicKeyOut.toString(Charsets.UTF_8.name())

        encryptedPrefs.edit()
            .putString(KEY_PRIVATE, privateKey)
            .putString(KEY_PUBLIC, publicKey)
            .apply()

        return publicKey
    }

    override fun clearKeyPair() {
        encryptedPrefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "ssh_key_store"
        private const val KEY_PRIVATE = "ssh_private_key"
        private const val KEY_PUBLIC = "ssh_public_key"
    }
}
