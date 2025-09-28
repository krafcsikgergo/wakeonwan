package hu.krafcsikgergo.wakeonwan.services.sender

import android.util.Log
import com.google.gson.Gson
import hu.krafcsikgergo.wakeonwan.services.LogManager
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Repository interface for network operations.
 * Handles HTTP requests to remote servers for wake-up, shutdown, and health checks.
 */
interface NetworkRepository {

    /**
     * Sends a wake-up request to the remote server.
     * @param baseUrl The base URL for the server (e.g., "http://192.168.1.100:8080")
     * @return Result containing success message or error
     */
    suspend fun wakeUpRemoteServer(baseUrl: String): Result<String>

    /**
     * Sends a shutdown request to the remote server.
     * @param baseUrl The base URL for the server (e.g., "http://192.168.1.100:8080")
     * @return Result containing success message or error
     */
    suspend fun shutDownRemoteServer(baseUrl: String): Result<String>

    /**
     * Checks if the server is reachable and responsive.
     * @param baseUrl The base URL for the server (e.g., "http://192.168.1.100:8080")
     * @return true if server is healthy, false otherwise
     */
    suspend fun checkKtorAppHealth(baseUrl: String): Boolean

    /**
     * Checks the server status endpoint.
     * @param baseUrl The base URL for the server (e.g., "http://192.168.1.100:8080")
     * @return true if server status endpoint responds, false otherwise
     */
    suspend fun getServerStatus(baseUrl: String): Boolean
}

/**
 * Implementation of NetworkRepository that handles HTTP operations.
 * This implementation uses Ktor client for network communication.
 */
class NetworkRepositoryImpl(
    private val httpClient: HttpClient,
    private val logManager: LogManager
) : NetworkRepository {

    override suspend fun wakeUpRemoteServer(baseUrl: String): Result<String> {
        return try {
            logManager.d("NetworkRepository", "Making wake-up request to: $baseUrl/wakeup")
            val response = httpClient.get("$baseUrl/wakeup")
            val jsonResponse = response.bodyAsText()

            logManager.d("NetworkRepository", "Wake-up response: $jsonResponse")
            logManager.d("NetworkRepository", "Response status: ${response.status}")

            val message = parseResponseMessage(jsonResponse)
            Result.success(message)

        } catch (e: Exception) {
            logManager.e("NetworkRepository", "Wake-up error: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun shutDownRemoteServer(baseUrl: String): Result<String> {
        return try {
            logManager.d("NetworkRepository", "Making shutdown request to: $baseUrl/shutdown")
            val response = httpClient.get("$baseUrl/shutdown")
            val jsonResponse = response.bodyAsText()

            logManager.d("NetworkRepository", "Shutdown response: $jsonResponse")
            logManager.d("NetworkRepository", "Response status: ${response.status}")

            val message = parseResponseMessage(jsonResponse)
            Result.success(message)

        } catch (e: Exception) {
            logManager.e("NetworkRepository", "Shutdown error: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun checkKtorAppHealth(baseUrl: String): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/")
            val isHealthy = response.status.value in 200..299
            logManager.d(
                "NetworkRepository",
                "Ktor Mobile server check: $isHealthy, response status: ${response.status}"
            )
            isHealthy
        } catch (e: Exception) {
            logManager.e("NetworkRepository", "Health check error: ${e.message}")
            false
        }
    }

    override suspend fun getServerStatus(baseUrl: String): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/test-server")
            val isHealthy = response.status.value in 200..299
            logManager.d(
                "NetworkRepository",
                "Server status check: $isHealthy, response status: ${response.status}"
            )
            isHealthy
        } catch (e: Exception) {
            logManager.e("NetworkRepository", "Status check error: ${e.message}")
            false
        }
    }

    /**
     * Parses the server response to extract the message.
     * Handles both JSON and plain text responses.
     */
    private fun parseResponseMessage(jsonResponse: String): String {
        return try {
            if (jsonResponse.startsWith("{") && jsonResponse.contains("message")) {
                val gson = Gson()
                gson.fromJson(jsonResponse, StatusResponse::class.java).message
            } else {
                // It's a plain text response or error page
                "Server response: $jsonResponse"
            }
        } catch (e: Exception) {
            logManager.w("NetworkRepository", "Failed to parse response as JSON: ${e.message}")
            "Server response: $jsonResponse"
        }
    }
}