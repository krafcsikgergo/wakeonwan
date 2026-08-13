package hu.krafcsikgergo.wakeonwan.sender.services

import com.google.gson.Gson
import hu.krafcsikgergo.wakeonwan.common.model.Schedule
import hu.krafcsikgergo.wakeonwan.common.model.StatusResponse
import hu.krafcsikgergo.wakeonwan.common.services.LogManager
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

private const val PAIRING_TOKEN_HEADER = "X-WOL-Token"
private const val UNPAIRED_MESSAGE = "Not paired with this receiver — scan its QR code again to re-pair"

private fun HttpRequestBuilder.applyPairingToken(token: String?) {
    if (!token.isNullOrBlank()) {
        header(PAIRING_TOKEN_HEADER, token)
    }
}

private val HttpResponse.isUnauthorized: Boolean
    get() = status == HttpStatusCode.Unauthorized

/**
 * Repository interface for network operations.
 * Handles HTTP requests to remote servers for wake-up, shutdown, health checks, and schedule management.
 * Every endpoint except the plain health check requires the receiver's pairing token.
 */
interface NetworkRepository {

    /**
     * Sends a wake-up request to the remote server.
     * @param baseUrl The base URL for the server (e.g., "http://192.168.1.100:8080")
     * @param token The pairing token for this server, if paired
     * @return Result containing success message or error
     */
    suspend fun wakeUpRemoteServer(baseUrl: String, token: String?): Result<String>

    /**
     * Sends a shutdown request to the remote server.
     * @param baseUrl The base URL for the server (e.g., "http://192.168.1.100:8080")
     * @param token The pairing token for this server, if paired
     * @return Result containing success message or error
     */
    suspend fun shutDownRemoteServer(baseUrl: String, token: String?): Result<String>

    /**
     * Checks if the server is reachable and responsive.
     * @param baseUrl The base URL for the server (e.g., "http://192.168.1.100:8080")
     * @return true if server is healthy, false otherwise
     */
    suspend fun checkKtorAppHealth(baseUrl: String): Boolean

    /**
     * Checks the target server's connectivity via the receiver: ping first, then
     * (only if that succeeds) a plain SSH connect/disconnect.
     * @param baseUrl The base URL for the server (e.g., "http://192.168.1.100:8080")
     * @param token The pairing token for this server, if paired
     * @return the ping and SSH reachability of the target server
     */
    suspend fun getServerStatus(baseUrl: String, token: String?): ServerConnectionStatus

    /**
     * Retrieves all schedules from the remote server.
     * @param baseUrl The base URL for the server (e.g., "http://192.168.1.100:8080")
     * @param token The pairing token for this server, if paired
     * @return Result containing list of schedules or error
     */
    suspend fun getSchedules(baseUrl: String, token: String?): Result<List<Schedule>>

    /**
     * Creates a new schedule on the remote server.
     * @param baseUrl The base URL for the server
     * @param schedule The schedule to create
     * @param token The pairing token for this server, if paired
     * @return Result containing the created schedule with its assigned ID or error
     */
    suspend fun createSchedule(baseUrl: String, schedule: Schedule, token: String?): Result<Schedule>

    /**
     * Updates an existing schedule on the remote server.
     * @param baseUrl The base URL for the server
     * @param scheduleId The ID of the schedule to update
     * @param schedule The updated schedule data
     * @param token The pairing token for this server, if paired
     * @return Result containing the updated schedule or error
     */
    suspend fun updateSchedule(baseUrl: String, scheduleId: Int, schedule: Schedule, token: String?): Result<Schedule>

    /**
     * Deletes a schedule from the remote server.
     * @param baseUrl The base URL for the server
     * @param scheduleId The ID of the schedule to delete
     * @param token The pairing token for this server, if paired
     * @return Result containing Unit on success or error
     */
    suspend fun deleteSchedule(baseUrl: String, scheduleId: Int, token: String?): Result<Unit>
}

data class ServerConnectionStatus(
    val pingSuccess: Boolean,
    val sshSuccess: Boolean,
    val message: String,
    val isUnauthorized: Boolean = false
)

/**
 * Implementation of NetworkRepository that handles HTTP operations.
 * This implementation uses Ktor client for network communication.
 */
class NetworkRepositoryImpl(
    private val httpClient: HttpClient,
    private val logManager: LogManager
) : NetworkRepository {

    override suspend fun wakeUpRemoteServer(baseUrl: String, token: String?): Result<String> {
        return try {
            logManager.d("NetworkRepository", "Making wake-up request to: $baseUrl/wakeup")
            val response = httpClient.get("$baseUrl/wakeup") { applyPairingToken(token) }
            val jsonResponse = response.bodyAsText()

            logManager.d("NetworkRepository", "Wake-up response: $jsonResponse")
            logManager.d("NetworkRepository", "Response status: ${response.status}")

            if (response.isUnauthorized) {
                return Result.failure(Exception(UNPAIRED_MESSAGE))
            }

            val message = parseResponseMessage(jsonResponse)
            Result.success(message)

        } catch (e: Exception) {
            logManager.e("NetworkRepository", "Wake-up error: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun shutDownRemoteServer(baseUrl: String, token: String?): Result<String> {
        return try {
            logManager.d("NetworkRepository", "Making shutdown request to: $baseUrl/shutdown")
            val response = httpClient.get("$baseUrl/shutdown") { applyPairingToken(token) }
            val jsonResponse = response.bodyAsText()

            logManager.d("NetworkRepository", "Shutdown response: $jsonResponse")
            logManager.d("NetworkRepository", "Response status: ${response.status}")

            if (response.isUnauthorized) {
                return Result.failure(Exception(UNPAIRED_MESSAGE))
            }

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

    override suspend fun getServerStatus(baseUrl: String, token: String?): ServerConnectionStatus {
        return try {
            val response = httpClient.get("$baseUrl/test-server") { applyPairingToken(token) }
            val jsonResponse = response.bodyAsText()
            logManager.d(
                "NetworkRepository",
                "Server status check response: $jsonResponse, response status: ${response.status}"
            )

            if (response.isUnauthorized) {
                return ServerConnectionStatus(
                    pingSuccess = false,
                    sshSuccess = false,
                    message = UNPAIRED_MESSAGE,
                    isUnauthorized = true
                )
            }

            val gson = Gson()
            val responseMap = gson.fromJson<Map<String, Any>>(
                jsonResponse,
                object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            )

            ServerConnectionStatus(
                pingSuccess = responseMap["ping"] as? Boolean ?: false,
                sshSuccess = responseMap["ssh"] as? Boolean ?: false,
                message = responseMap["message"] as? String ?: ""
            )
        } catch (e: Exception) {
            logManager.e("NetworkRepository", "Status check error: ${e.message}")
            ServerConnectionStatus(pingSuccess = false, sshSuccess = false, message = e.message ?: "Unknown error")
        }
    }

    override suspend fun getSchedules(baseUrl: String, token: String?): Result<List<Schedule>> {
        return try {
            logManager.d("NetworkRepository", "Fetching schedules from: $baseUrl/schedules")
            val response = httpClient.get("$baseUrl/schedules") { applyPairingToken(token) }

            if (response.isUnauthorized) {
                return Result.failure(Exception(UNPAIRED_MESSAGE))
            }

            if (response.status.value !in 200..299) {
                logManager.e("NetworkRepository", "Failed to get schedules: ${response.status}")
                return Result.failure(Exception("Failed to get schedules: HTTP ${response.status.value}"))
            }

            val jsonResponse = response.bodyAsText()
            logManager.d("NetworkRepository", "Received schedules response: $jsonResponse")

            // Parse JSON array of schedules
            val gson = Gson()
            val scheduleListType = object : com.google.gson.reflect.TypeToken<List<Schedule>>() {}.type
            val schedules: List<Schedule> = gson.fromJson(jsonResponse, scheduleListType)

            logManager.d("NetworkRepository", "Successfully parsed ${schedules.size} schedules")
            Result.success(schedules)

        } catch (e: Exception) {
            logManager.e("NetworkRepository", "Error getting schedules: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun createSchedule(baseUrl: String, schedule: Schedule, token: String?): Result<Schedule> {
        return try {
            val gson = Gson()
            logManager.d("NetworkRepository", "Creating schedule at: $baseUrl/schedules")
            logManager.d("NetworkRepository", "Schedule data: ${gson.toJson(schedule)}")

            val response = httpClient.post("$baseUrl/schedules") {
                applyPairingToken(token)
                contentType(ContentType.Application.Json)
                setBody(gson.toJson(schedule))
            }

            if (response.isUnauthorized) {
                return Result.failure(Exception(UNPAIRED_MESSAGE))
            }

            if (response.status.value !in 200..299) {
                val errorBody = response.bodyAsText()
                logManager.e("NetworkRepository", "Failed to create schedule: ${response.status}, body: $errorBody")
                return Result.failure(Exception("Failed to create schedule: $errorBody"))
            }

            val jsonResponse = response.bodyAsText()
            logManager.d("NetworkRepository", "Create schedule response: $jsonResponse")

            // The server returns a response with "message" and "id"
            val responseMap = gson.fromJson<Map<String, Any>>(
                jsonResponse,
                object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            )
            val createdId = (responseMap["id"] as? Double)?.toInt() ?: schedule.id

            // Return the schedule with the server-assigned ID
            val createdSchedule = schedule.copy(id = createdId)
            logManager.d("NetworkRepository", "Successfully created schedule with ID: $createdId")

            Result.success(createdSchedule)

        } catch (e: Exception) {
            logManager.e("NetworkRepository", "Error creating schedule: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateSchedule(
        baseUrl: String,
        scheduleId: Int,
        schedule: Schedule,
        token: String?
    ): Result<Schedule> {
        return try {
            val gson = Gson()
            logManager.d("NetworkRepository", "Updating schedule at: $baseUrl/schedules/$scheduleId")
            logManager.d("NetworkRepository", "Schedule data: ${gson.toJson(schedule)}")

            val response = httpClient.put("$baseUrl/schedules/$scheduleId") {
                applyPairingToken(token)
                contentType(ContentType.Application.Json)
                setBody(gson.toJson(schedule))
            }

            if (response.isUnauthorized) {
                return Result.failure(Exception(UNPAIRED_MESSAGE))
            }

            if (response.status.value !in 200..299) {
                val errorBody = response.bodyAsText()
                logManager.e("NetworkRepository", "Failed to update schedule: ${response.status}, body: $errorBody")
                return Result.failure(Exception("Failed to update schedule: $errorBody"))
            }

            val jsonResponse = response.bodyAsText()
            logManager.d("NetworkRepository", "Update schedule response: $jsonResponse")

            // The server returns a response with "message" and "schedule"
            val responseMap = gson.fromJson<Map<String, Any>>(
                jsonResponse,
                object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            )

            // Extract the schedule from the response
            val scheduleData = responseMap["schedule"]
            val updatedSchedule = if (scheduleData != null) {
                gson.fromJson(gson.toJson(scheduleData), Schedule::class.java)
            } else {
                // If no schedule in response, return the one we sent with the correct ID
                schedule.copy(id = scheduleId)
            }

            logManager.d("NetworkRepository", "Successfully updated schedule with ID: $scheduleId")
            Result.success(updatedSchedule)

        } catch (e: Exception) {
            logManager.e("NetworkRepository", "Error updating schedule: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteSchedule(baseUrl: String, scheduleId: Int, token: String?): Result<Unit> {
        return try {
            logManager.d("NetworkRepository", "Deleting schedule from: $baseUrl/schedules/$scheduleId")

            val response = httpClient.delete("$baseUrl/schedules/$scheduleId") { applyPairingToken(token) }

            if (response.isUnauthorized) {
                return Result.failure(Exception(UNPAIRED_MESSAGE))
            }

            if (response.status.value !in 200..299) {
                val errorBody = response.bodyAsText()
                logManager.e("NetworkRepository", "Failed to delete schedule: ${response.status}, body: $errorBody")
                return Result.failure(Exception("Failed to delete schedule: $errorBody"))
            }

            val jsonResponse = response.bodyAsText()
            logManager.d("NetworkRepository", "Delete schedule response: $jsonResponse")

            Result.success(Unit)

        } catch (e: Exception) {
            logManager.e("NetworkRepository", "Error deleting schedule: ${e.message}", e)
            Result.failure(e)
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
