package hu.krafcsikgergo.wakeonwan.sender.services

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Stores the pairing token received from each receiver's QR code, keyed by the
 * server's id, in an encrypted, app-private store.
 */
interface PairingTokenStore {
    fun getToken(serverId: String): String?
    fun saveToken(serverId: String, token: String)
    fun removeToken(serverId: String)
}

class PairingTokenStoreImpl(
    private val context: Context
) : PairingTokenStore {

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

    override fun getToken(serverId: String): String? = encryptedPrefs.getString(serverId, null)

    override fun saveToken(serverId: String, token: String) {
        encryptedPrefs.edit().putString(serverId, token).apply()
    }

    override fun removeToken(serverId: String) {
        encryptedPrefs.edit().remove(serverId).apply()
    }

    companion object {
        private const val PREFS_NAME = "pairing_token_store"
    }
}
