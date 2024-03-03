package hu.krafcsikgergo.wakeonwan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import hu.krafcsikgergo.wakeonwan.ui.theme.WakeOnWANTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.KtorServerData
import hu.krafcsikgergo.wakeonwan.services.ServerData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            var dataLoaded by remember { mutableStateOf(false) }
            LaunchedEffect(dataLoaded) {
                // Get the IP address and port of the Ktor server from the DataStore
                KtorServerData.ipAddress =
                    DataStoreManager.getInstance(this@MainActivity).getString("ktorIpAddress")
                        ?: "192.168.0.1"
                KtorServerData.port =
                    DataStoreManager.getInstance(this@MainActivity).getString("communicationPort")
                        ?.toInt() ?: 8080

                // Get the Server data from the DataStore
                ServerData.ipAddress =
                    DataStoreManager.getInstance(this@MainActivity).getString("serverIpAddress")
                        ?: "192.168.0.1"
                ServerData.macAddress =
                    DataStoreManager.getInstance(this@MainActivity).getString("serverMacAddress")
                        ?: "00:00:00:00:00:00"
                ServerData.sshPort =
                    DataStoreManager.getInstance(this@MainActivity).getString("serverSSHPort")
                        ?.toInt()
                        ?: 22
                ServerData.username =
                    DataStoreManager.getInstance(this@MainActivity).getString("serverUsername")
                        ?: ""
                ServerData.password =
                    DataStoreManager.getInstance(this@MainActivity).getString("serverPassword")
                        ?: ""

                dataLoaded = true
            }
            WakeOnWANTheme {
                if (dataLoaded) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NavHost()
                    }
                }
            }
        }
    }
}

@Composable
fun NavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavigationItem.Sender.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(
            NavigationItem.Sender.route,
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) {
            SenderScreen() {
                navController.navigate(NavigationItem.Receiver.route) {
                    popUpTo(NavigationItem.Sender.route) {
                        inclusive = true
                    }
                }
            }
        }

        composable(
            NavigationItem.Receiver.route,
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) {
            ReceiverScreen() {
                navController.navigate(NavigationItem.Sender.route) {
                    popUpTo(NavigationItem.Receiver.route) {
                        inclusive = true
                    }
                }
            }
        }
    }
}


sealed class NavigationItem(val route: String) {
    object Sender : NavigationItem("sender")
    object Receiver : NavigationItem("receiver")
}