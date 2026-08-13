package hu.krafcsikgergo.wakeonwan.sender.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.senderDataStore: DataStore<Preferences> by preferencesDataStore(name = "sender")

interface DataStoreManager {
    suspend fun getKtorServers(): List<KtorServerData>
    suspend fun addKtorServer(ktorServerData: KtorServerData)
    suspend fun removeKtorServer(id: String)
}

class DataStoreManagerImpl(
    private val context: Context
) : DataStoreManager {
    private val json = Json { ignoreUnknownKeys = true }

    private val senderDataStore: DataStore<Preferences> get() = context.senderDataStore

    private suspend fun saveString(dataStore: DataStore<Preferences>, key: String, value: String) {
        val preferenceKey = stringPreferencesKey(key)
        dataStore.edit { data ->
            data[preferenceKey] = value
        }
    }

    private suspend fun readString(dataStore: DataStore<Preferences>, key: String): String? {
        val preferenceKey = stringPreferencesKey(key)
        val preferences = dataStore.data.first()
        return preferences[preferenceKey]
    }

    override suspend fun getKtorServers(): List<KtorServerData> {
        val encoded = readString(senderDataStore, "ktorServers") ?: return emptyList()
        return json.decodeFromString(encoded)
    }

    override suspend fun addKtorServer(ktorServerData: KtorServerData) {
        val servers = getKtorServers().toMutableList()
        servers.add(ktorServerData)
        val encoded = json.encodeToString(servers)
        saveString(senderDataStore, "ktorServers", encoded)
    }

    override suspend fun removeKtorServer(id: String) {
        val servers = getKtorServers().toMutableList()
        servers.removeAll { it.id == id }
        val encoded = json.encodeToString(servers)
        saveString(senderDataStore, "ktorServers", encoded)
    }
}
