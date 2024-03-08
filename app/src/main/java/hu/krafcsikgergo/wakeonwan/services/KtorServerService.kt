package hu.krafcsikgergo.wakeonwan.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import hu.krafcsikgergo.wakeonwan.R
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
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

class KtorServerService : Service() {

    private lateinit var server: NettyApplicationEngine

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
        server = embeddedServer(Netty, port = 9753, module = Application::module)
        server.start()
        Log.d(
            "Server",
            "Server started at ${server.environment.connectors.first().host}:${server.environment.connectors.first().port}"
        )
    }

    private fun stopServer() {
        server.stop(500, 1000)
        Log.d("Server", "Server stopped")
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, KtorServerService::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE)

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
}


fun Application.module() {
    install(ContentNegotiation) {
        gson()
    }

    routing {
        get("/") {
            call.respond(HttpStatusCode.OK, mapOf("message" to "Ktor server is running"))
        }

        get("/wakeup") {
            Log.d("Server", "Received request to wake up")

            // get the mac address and ip address from the request
            val macAddress = ServerData.macAddress
            val ipAddress = ServerData.ipAddress

            if (macAddress == null || ipAddress == null) {
                // respond with error status 4** code
                call.respond(
                    HttpStatusCode.PreconditionFailed,
                    mapOf("message" to "Missing macAddress or ipAddress on host device")
                )
                return@get
            }
            // Assuming sendWakeOnLANPacket is a suspend function, you may want to call it using withContext
            withContext(Dispatchers.IO) {
                sendWakeOnLANPacket(ipAddress, macAddress)
            }
            call.respond(HttpStatusCode.OK, mapOf("message" to "Wake-up request sent successfully"))
        }

        get("/test") {
            Log.d("Server", "Received request to test connection")

            val ipAddress = ServerData.ipAddress

            if (ipAddress == null) {
                call.respond(
                    HttpStatusCode.PreconditionFailed,
                    mapOf("message" to "Missing ipAddress on host device")
                )
                return@get
            }
            val isReachable = withContext(Dispatchers.IO) {
                PingUtil.ping(ipAddress)
            }

            Log.d("Server", "Host is reachable: $isReachable")
            if (isReachable) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Host is reachable"))
            } else {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("message" to "Host is not reachable")
                )
            }
        }

        get("/shutdown"){
            Log.d("Server", "Received request to shutdown")
            val sshPort = ServerData.sshPort
            val ipAddress = ServerData.ipAddress
            val username = ServerData.username
            val password = ServerData.password

            val isShutdown = withContext(Dispatchers.IO) {
                val sshManager = SSHManager(username, ipAddress, sshPort, password)
                sshManager.executeCommand("sudo shutdown now")
            }
            if (isShutdown) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Shutdown request sent successfully"))
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to send shutdown request")
                )
            }
        }

        post("/schedules") {
            val schedule = call.receive<Schedule>()
            ServerData.schedules.add(schedule)
            call.respond(HttpStatusCode.OK, mapOf("message" to "Schedule added successfully"))
        }

        get("/schedules") {
            call.respond(HttpStatusCode.OK, ServerData.schedules)
        }

        delete("/schedules/{index}") {
            val index = call.parameters["index"]?.toInt() ?: -1
            if (index < 0 || index >= ServerData.schedules.size) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid index"))
            } else {
                ServerData.schedules.removeAt(index)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Schedule deleted successfully"))
            }
        }
    }
}


