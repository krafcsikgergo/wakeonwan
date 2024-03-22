package hu.krafcsikgergo.wakeonwan.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

class DataStoreManager private constructor(val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: DataStoreManager? = null

        fun getInstance(context: Context): DataStoreManager {
            return INSTANCE ?: synchronized(this) {
                val instance = DataStoreManager(context)
                INSTANCE = instance
                instance
            }
        }
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    suspend fun getString(keyString: String): String? {
        val key = stringPreferencesKey(keyString)
        val preferences = context.dataStore.data.first()
        return preferences[key]
    }

    suspend fun writeString(keyString: String, value: String) {
        val key = stringPreferencesKey(keyString)
        context.dataStore.edit { settings ->
            settings[key] = value
        }
    }

}