package hu.krafcsikgergo.wakeonwan.services

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class NetworkManager {

    suspend fun wakeUpRemoteServer(toast: (String) -> Unit) {
        val response = ApiImplementation.getInstance().wakeUp()
        val jsonResponse = response.body()?.string()
        val gson = Gson()
        val message = gson.fromJson(jsonResponse, StatusResponse::class.java).message
        Log.d(
            "Wake up",
            message
        )
        withContext(Dispatchers.Main) {
            toast(message)
        }
    }

    suspend fun getServerStatus(): Boolean {
        try {
            ApiImplementation.getInstance().getStatus()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    suspend fun checkKtorServerHealth(): Boolean {
        try {
            val response = ApiImplementation.getInstance().checkHealth()
            return response.isSuccessful
        } catch (e: Exception) {
            return false
        }
    }

    suspend fun shutDownRemoteServer(toast: (String) -> Unit) {
        try {
            val response = ApiImplementation.getInstance().shutDown()
            val jsonResponse = response.body()?.string()
            val gson = Gson()
            val message = gson.fromJson(jsonResponse, StatusResponse::class.java).message
            Log.d(
                "Shut down",
                message
            )
            withContext(Dispatchers.Main) {
                toast(message)
            }
        } catch (e: Exception) {
            Log.e("Shut down", e.message.toString())
            withContext(Dispatchers.Main) {
                toast("Something went wrong")
            }
        }
    }

}