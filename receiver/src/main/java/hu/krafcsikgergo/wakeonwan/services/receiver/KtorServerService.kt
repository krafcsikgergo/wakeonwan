package hu.krafcsikgergo.wakeonwan.services.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import hu.krafcsikgergo.wakeonwan.R
import hu.krafcsikgergo.wakeonwan.common.model.Schedule
import hu.krafcsikgergo.wakeonwan.common.model.defaultKtorPort
import hu.krafcsikgergo.wakeonwan.common.services.LogManager
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.net.InetAddress

class KtorServerService : Service() {
    private val TAG = "KtorServerService"
    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private val dataStoreManager: DataStoreManager by inject()
    private val sshManager: SSHManager by inject()
    private val wakeOnLanService: WakeOnLanService by inject()
    private val scheduleManager: ScheduleManager by inject()
    private val logManager: LogManager by inject()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "ktor_server_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startServer()
        startForegroundService()
        logManager.d(TAG, "Service started")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        logManager.d(TAG, "Service stopped")
    }

    private fun startServer() {
        server = embeddedServer(Netty, port = defaultKtorPort) {
            configureApplication()
        }
        server.start()
        logManager.d(TAG, "Server started on port ${defaultKtorPort}")
    }

    private fun stopServer() {
        server.stop(500, 1000)
        logManager.d(TAG, "Server stopped")
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, KtorServerService::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Ktor Server Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
            .setContentTitle("Ktor Server Service")
            .setContentText("Server is running in the background")
            .setSmallIcon(R.mipmap.launch_icon)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun ping(host: String, timeout: Int = 1000): Boolean {
        return try {
            val inetAddress = InetAddress.getByName(host)
            inetAddress.isReachable(timeout)
        } catch (e: Exception) {
            false
        }
    }

    private fun Application.configureApplication() {
        install(ContentNegotiation) {
            gson()
        }

        routing {
            // Testing
            get("/") {
                logManager.d(TAG, "Received request to test ktor running")
                call.respond(
                    HttpStatusCode.Companion.OK,
                    mapOf("message" to "Ktor server is running")
                )
            }

            // Wake-on-LAN
            get("/wakeup") {
                logManager.d(TAG, "Received request to wake up")

                // Use DataStoreManager instead of global ServerData
                val serverData = dataStoreManager.getServerData()

                if (serverData == null) {
                    call.respond(
                        HttpStatusCode.Companion.PreconditionFailed,
                        mapOf("message" to "Server configuration not found")
                    )
                    return@get
                }

                val result = wakeOnLanService.sendWakeOnLanPacket(serverData)

                result.fold(
                    onSuccess = { message ->
                        call.respond(
                            HttpStatusCode.Companion.OK,
                            mapOf("message" to message)
                        )
                    },
                    onFailure = { exception ->
                        call.respond(
                            HttpStatusCode.Companion.InternalServerError,
                            mapOf("message" to "Wake-up request failed: ${exception.message}")
                        )
                    }
                )
            }

            // Test server connection (ping + SSH)
            get("/test-server") {
                logManager.d(TAG, "Received request to test server connection")

                val serverData = dataStoreManager.getServerData()
                val ipAddress = serverData?.ipAddress

                if (ipAddress == null) {
                    call.respond(
                        HttpStatusCode.Companion.PreconditionFailed,
                        mapOf("message" to "Missing ipAddress on host device")
                    )
                    return@get
                }

                val isReachable = withContext(Dispatchers.IO) {
                    ping(ipAddress)
                }

                logManager.d(TAG, "Host is reachable via ping: $isReachable")

                // If ping fails, don't bother testing SSH
                if (!isReachable) {
                    val response = mapOf(
                        "ping" to false,
                        "ssh" to false,
                        "message" to "Host is not reachable via ping, SSH test skipped"
                    )
                    call.respond(HttpStatusCode.Companion.ServiceUnavailable, response)
                    return@get
                }

                val sshConnectable = withContext(Dispatchers.IO) {
                    sshManager.testConnection(serverData)
                }

                logManager.d(TAG, "Host is reachable via SSH: $sshConnectable")

                val response = mutableMapOf<String, Any>()
                response["ping"] = true
                response["ssh"] = sshConnectable

                if (sshConnectable) {
                    response["message"] = "Host is fully reachable (ping and SSH)"
                    call.respond(HttpStatusCode.Companion.OK, response)
                } else {
                    response["message"] = "Host is reachable via ping but SSH connection failed"
                    call.respond(HttpStatusCode.Companion.PartialContent, response)
                }
            }

            // Shutdown via SSH
            get("/shutdown") {
                logManager.d(TAG, "Received request to shutdown")

                val serverData = dataStoreManager.getServerData()

                if (serverData == null) {
                    call.respond(
                        HttpStatusCode.Companion.PreconditionFailed,
                        mapOf("message" to "Server configuration not found")
                    )
                    return@get
                }

                val shutdownResult = withContext(Dispatchers.IO) {
                    wakeOnLanService.executeShutdownCommand(serverData)
                }

                if (shutdownResult.isSuccess) {
                    call.respond(
                        HttpStatusCode.Companion.OK,
                        mapOf("message" to "Shutdown request sent successfully")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.Companion.InternalServerError,
                        mapOf("message" to "Failed to send shutdown request")
                    )
                }
            }

            // Get saved schedules
            get("/schedules") {
                logManager.d(TAG, "Received request to get schedules")

                try {
                    val schedules = scheduleManager.getAllSchedules()
                    call.respond(
                        HttpStatusCode.Companion.OK,
                        schedules
                    )
                } catch (e: Exception) {
                    logManager.e(TAG, "Failed to retrieve schedules", e)
                    call.respond(
                        HttpStatusCode.Companion.InternalServerError,
                        mapOf("message" to "Failed to retrieve schedules: ${e.message}")
                    )
                }
            }

            // Add a new schedule
            post("/schedules") {
                logManager.d(TAG, "Received request to add schedule")

                try {
                    val schedule = call.receive<Schedule>()

                    // Create schedule via ScheduleManager (includes validation and DataStore persistence)
                    val result = scheduleManager.createSchedule(
                        time = schedule.timeInLocalTime,
                        turnOn = schedule.turnOn,
                        days = schedule.days,
                        enabled = schedule.enabled
                    )

                    result.fold(
                        onSuccess = { createdSchedule ->
                            // Schedule alarms for the new schedule
                            scheduleManager.scheduleAlarms(
                                this@KtorServerService,
                                listOf(createdSchedule)
                            )

                            logManager.d(TAG, "Schedule ${createdSchedule.id} added and alarms scheduled")
                            call.respond(
                                HttpStatusCode.Companion.Created,
                                mapOf(
                                    "message" to "Schedule added successfully",
                                    "id" to createdSchedule.id
                                )
                            )
                        },
                        onFailure = { exception ->
                            logManager.e(TAG, "Failed to create schedule", exception)
                            call.respond(
                                HttpStatusCode.Companion.BadRequest,
                                exception.message ?: "Failed to create schedule"
                            )
                        }
                    )
                } catch (e: Exception) {
                    logManager.e(TAG, "Failed to add schedule", e)
                    call.respond(
                        HttpStatusCode.Companion.InternalServerError,
                        mapOf("message" to "Failed to add schedule: ${e.message}")
                    )
                }
            }

            // Update an existing schedule
            put("/schedules/{id}") {
                logManager.d(TAG, "Received request to update schedule")

                try {
                    val scheduleIdStr = call.parameters["id"]
                    if (scheduleIdStr == null) {
                        call.respond(
                            HttpStatusCode.Companion.BadRequest,
                            mapOf("message" to "Schedule ID is required")
                        )
                        return@put
                    }

                    val scheduleId = scheduleIdStr.toIntOrNull()
                    if (scheduleId == null) {
                        call.respond(
                            HttpStatusCode.Companion.BadRequest,
                            mapOf("message" to "Invalid schedule ID format")
                        )
                        return@put
                    }

                    val schedule = call.receive<Schedule>()

                    // Validate that the schedule ID in the URL matches the one in the body (if present)
                    if (schedule.id != 0 && schedule.id != scheduleId) {
                        call.respond(
                            HttpStatusCode.Companion.BadRequest,
                            mapOf("message" to "Schedule ID in URL does not match schedule ID in body")
                        )
                        return@put
                    }

                    // Update schedule via ScheduleManager (includes validation and DataStore persistence)
                    val result = scheduleManager.updateSchedule(
                        scheduleId = scheduleId,
                        time = schedule.timeInLocalTime,
                        turnOn = schedule.turnOn,
                        days = schedule.days,
                        enabled = schedule.enabled
                    )

                    result.fold(
                        onSuccess = { updatedSchedule ->
                            // Update alarms based on enabled state
                            if (updatedSchedule.enabled) {
                                // Schedule alarms for the enabled schedule
                                scheduleManager.scheduleAlarms(this@KtorServerService, listOf(updatedSchedule))
                            } else {
                                // Cancel alarms for the disabled schedule
                                scheduleManager.cancelAlarm(this@KtorServerService, scheduleId)
                            }

                            logManager.d(TAG, "Schedule $scheduleId updated successfully")
                            call.respond(
                                HttpStatusCode.Companion.OK,
                                mapOf<String, Any>(
                                    "message" to "Schedule updated successfully",
                                    "schedule" to updatedSchedule
                                )
                            )
                        },
                        onFailure = { exception ->
                            logManager.e(TAG, "Failed to update schedule", exception)
                            val statusCode = if (exception.message?.contains("not found") == true) {
                                HttpStatusCode.Companion.NotFound
                            } else {
                                HttpStatusCode.Companion.InternalServerError
                            }
                            call.respond(
                                statusCode,
                                mapOf<String, String>("message" to (exception.message ?: "Failed to update schedule"))
                            )
                        }
                    )
                } catch (e: Exception) {
                    logManager.e(TAG, "Failed to update schedule", e)
                    call.respond(
                        HttpStatusCode.Companion.InternalServerError,
                        mapOf<String, String>("message" to "Failed to update schedule: ${e.message}")
                    )
                }
            }

            // Delete a schedule by ID
            delete("/schedules/{id}") {
                logManager.d(TAG, "Received request to delete schedule")

                try {
                    val scheduleIdStr = call.parameters["id"]
                    if (scheduleIdStr == null) {
                        call.respond(
                            HttpStatusCode.Companion.BadRequest,
                            mapOf("message" to "Schedule ID is required")
                        )
                        return@delete
                    }

                    val scheduleId = scheduleIdStr.toIntOrNull()
                    if (scheduleId == null) {
                        call.respond(
                            HttpStatusCode.Companion.BadRequest,
                            mapOf("message" to "Invalid schedule ID format")
                        )
                        return@delete
                    }

                    // Delete schedule via ScheduleManager (includes validation and DataStore persistence)
                    val result = scheduleManager.deleteSchedule(scheduleId)

                    result.fold(
                        onSuccess = {
                            // Cancel alarms for this schedule
                            scheduleManager.cancelAlarm(this@KtorServerService, scheduleId)

                            logManager.d(TAG, "Schedule $scheduleId deleted and alarms cancelled")
                            call.respond(
                                HttpStatusCode.Companion.OK,
                                mapOf("message" to "Schedule deleted successfully")
                            )
                        },
                        onFailure = { exception ->
                            logManager.e(TAG, "Failed to delete schedule", exception)
                            val statusCode = if (exception.message?.contains("not found") == true) {
                                HttpStatusCode.Companion.NotFound
                            } else {
                                HttpStatusCode.Companion.InternalServerError
                            }
                            call.respond(
                                statusCode,
                                exception.message ?: "Failed to delete schedule"
                            )
                        }
                    )
                } catch (e: Exception) {
                    logManager.e(TAG, "Failed to delete schedule", e)
                    call.respond(
                        HttpStatusCode.Companion.InternalServerError,
                        mapOf("message" to "Failed to delete schedule: ${e.message}")
                    )
                }
            }
        }
    }
}
