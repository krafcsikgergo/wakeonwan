package hu.krafcsikgergo.wakeonwan.services.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import hu.krafcsikgergo.wakeonwan.R
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.receiver.SSHManager
import hu.krafcsikgergo.wakeonwan.services.receiver.Schedule
import hu.krafcsikgergo.wakeonwan.services.receiver.defaultKtorPort
import hu.krafcsikgergo.wakeonwan.services.sendWakeOnLANPacket
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
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.net.InetAddress

class KtorServerService : Service() {
    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private val dataStoreManager: DataStoreManager by inject()
    private val sshManager: SSHManager by inject()

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
        Log.d("Server", "Service started")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        Log.d("Server", "Service stopped")
    }

    private fun startServer() {
        server = embeddedServer(Netty, port = defaultKtorPort) {
            configureApplication()
        }
        server.start()
        Log.d("Server", "Server started on port ${defaultKtorPort}")
    }

    private fun stopServer() {
        server.stop(500, 1000)
        Log.d("Server", "Server stopped")
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
            get("/") {
                call.respond(HttpStatusCode.Companion.OK, mapOf("message" to "Ktor server is running"))
            }

            get("/wakeup") {
                Log.d("Server", "Received request to wake up")

                // Use DataStoreManager instead of global ServerData
                val serverData = dataStoreManager.getServerData()

                if (serverData?.macAddress == null) {
                    call.respond(
                        HttpStatusCode.Companion.PreconditionFailed,
                        mapOf("message" to "Missing macAddress or ipAddress on host device")
                    )
                    return@get
                }

                withContext(Dispatchers.IO) {
                    sendWakeOnLANPacket(serverData.ipAddress, serverData.macAddress)
                }
                call.respond(
                    HttpStatusCode.Companion.OK,
                    mapOf("message" to "Wake-up request sent successfully")
                )
            }

            get("/test") {
                Log.d("Server", "Received request to test connection")

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

                Log.d("Server", "Host is reachable: $isReachable")
                if (isReachable) {
                    call.respond(HttpStatusCode.Companion.OK, mapOf("message" to "Host is reachable"))
                } else {
                    call.respond(
                        HttpStatusCode.Companion.ServiceUnavailable,
                        mapOf("message" to "Host is not reachable")
                    )
                }
            }

            get("/shutdown") {
                Log.d("Server", "Received request to shutdown")

                val serverData = dataStoreManager.getServerData()

                if (serverData == null) {
                    call.respond(
                        HttpStatusCode.Companion.PreconditionFailed,
                        mapOf("message" to "Server configuration not found")
                    )
                    return@get
                }

                val isShutdown = withContext(Dispatchers.IO) {
                    sshManager.executeCommand("sudo shutdown now")
                }

                if (isShutdown) {
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

            // For schedules, you might want to add them to DataStoreManager
            // or create a separate ScheduleManager that also uses DataStore
            post("/schedules") {
                val schedule = call.receive<Schedule>()
                // Instead of file operations, use DataStore
                // You might need to extend DataStoreManager to handle schedules
                dataStoreManager.saveSchedule(schedule)
                call.respond(HttpStatusCode.Companion.OK, mapOf("message" to "Schedule added successfully"))
            }

            get("/schedules") {
                // Read from DataStore instead of file
                val schedules = dataStoreManager.getSchedules()
                call.respond(HttpStatusCode.Companion.OK, schedules)
            }

            delete("/schedules/{id}") {
                val scheduleId = call.parameters["id"]?.toInt() ?: return@delete
                dataStoreManager.removeSchedule(scheduleId)
                call.respond(HttpStatusCode.Companion.OK, mapOf("message" to "Schedule deleted successfully"))
            }
        }
    }
}