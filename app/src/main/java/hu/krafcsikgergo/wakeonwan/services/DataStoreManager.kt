package hu.krafcsikgergo.wakeonwan.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import hu.krafcsikgergo.wakeonwan.services.receiver.Schedule
import hu.krafcsikgergo.wakeonwan.services.receiver.ServerData
import hu.krafcsikgergo.wakeonwan.services.sender.KtorServerData
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json


interface DataStoreManager {
    suspend fun saveLastPage(page: String)
    suspend fun getLastPage(): String?

    suspend fun saveServerData(serverData: ServerData)
    suspend fun getServerData(): ServerData?
    suspend fun saveSchedule(schedule: Schedule)
    suspend fun getSchedules(): List<Schedule>
    suspend fun removeSchedule(id: Int)

    suspend fun getKtorServers(): List<KtorServerData>
    suspend fun addKtorServer(ktorServerData: KtorServerData)
    suspend fun removeKtorServer(id: String)
}


class DataStoreManagerImpl(
    private val context: Context
) : DataStoreManager {
    private val TAG = "DataStoreManager"
    private val json = Json { ignoreUnknownKeys = true }

    private val Context.commonDataStore: DataStore<Preferences> by preferencesDataStore(name = "common")
    private val Context.receiverDataStore: DataStore<Preferences> by preferencesDataStore(name = "receiver")
    private val Context.senderDataStore: DataStore<Preferences> by preferencesDataStore(name = "sender")
    
    private val commonDataStore: DataStore<Preferences> get() = context.commonDataStore
    private val receiverDataStore: DataStore<Preferences> get() = context.receiverDataStore
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

    // COMMON
    override suspend fun saveLastPage(page: String) {
        saveString(commonDataStore, "lastPage", page)
    }
    
    override suspend fun getLastPage(): String? {
        return readString(commonDataStore, "lastPage")
    }


    // RECEIVER

    override suspend fun saveServerData(serverData: ServerData) {
        val encoded = json.encodeToString<ServerData>(serverData)
        saveString(receiverDataStore, "serverData", encoded)
    }

    override suspend fun getServerData(): ServerData? {
        val encoded = readString(receiverDataStore, "serverData") ?: return null
        return json.decodeFromString<ServerData>(encoded)
    }

    override suspend fun getSchedules(): List<Schedule> {
        val encoded = readString(receiverDataStore, "schedules") ?: return emptyList()
        return json.decodeFromString(encoded)
    }

    override suspend fun saveSchedule(schedule: Schedule) {
        val schedules = getSchedules().toMutableList()
        schedules.add(schedule)
        val encoded = json.encodeToString(schedules)
        saveString(receiverDataStore, "schedules", encoded)
    }

    override suspend fun removeSchedule(id: Int) {
        val schedules = getSchedules().toMutableList()
        schedules.removeAll { it.id == id }
        val encoded = json.encodeToString(schedules)
        saveString(receiverDataStore, "schedules", encoded)
    }

    // SENDER

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
