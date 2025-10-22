package hu.krafcsikgergo.wakeonwan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.ui.screens.ReceiverScreen
import hu.krafcsikgergo.wakeonwan.ui.screens.SchedulesScreen
import hu.krafcsikgergo.wakeonwan.ui.screens.SenderScreen
import hu.krafcsikgergo.wakeonwan.ui.screens.LogScreen
import hu.krafcsikgergo.wakeonwan.ui.theme.WakeOnWANTheme
import org.koin.core.component.KoinComponent

class MainActivity : ComponentActivity(), KoinComponent {

    val dataStoreManager: DataStoreManager by lazy { getKoin().get() }
    private var isDataLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val splashScreen = installSplashScreen()

        // Keep splash screen visible until data is loaded
        splashScreen.setKeepOnScreenCondition { !isDataLoaded }

        setContent {
            // Remove when https://issuetracker.google.com/issues/364713509 is fixed
            LaunchedEffect(isSystemInDarkTheme()) { enableEdgeToEdge() }
            WakeOnWANTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WakeOnWANNavigation(
                        dataStoreManager = dataStoreManager,
                        onDataLoaded = { isDataLoaded = true }
                    )
                }
            }
        }
    }
}

@Composable
fun WakeOnWANNavigation(
    dataStoreManager: DataStoreManager,
    onDataLoaded: () -> Unit
) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    // Load the last page asynchronously
    LaunchedEffect(Unit) {
        val loadedLastPage = dataStoreManager.getLastPage() ?: NavigationItem.Sender.route
        startDestination = when (loadedLastPage) {
            NavigationItem.Sender.route,
            NavigationItem.Receiver.route -> loadedLastPage

            else -> NavigationItem.Sender.route
        }
        // Notify that data is loaded
        onDataLoaded()
    }

    // Only show navigation once we have loaded the start destination
    startDestination?.let { destination ->
        NavHost(startDestination = destination, dataStoreManager = dataStoreManager)
    }
}

@Composable
fun NavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String,
    dataStoreManager: DataStoreManager
) {
    val coroutineScope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(
            NavigationItem.Sender.route,
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) {
            SenderScreen(
                navigateToReceiver = {
                    coroutineScope.launch {
                        dataStoreManager.saveLastPage(NavigationItem.Receiver.route)
                    }
                    navController.navigate(NavigationItem.Receiver.route) {
                        popUpTo(NavigationItem.Sender.route) {
                            inclusive = true
                        }
                    }
                },
                navigateToLogs = {
                    coroutineScope.launch {
                        dataStoreManager.saveLastPage(NavigationItem.Logs.route)
                    }
                    navController.navigate(NavigationItem.Logs.route)
                },
                navigateToSchedules = { serverId ->
                    navController.navigate("${NavigationItem.Schedules.route}?mode=sender&serverId=$serverId")
                }
            )
        }

        composable(
            NavigationItem.Receiver.route,
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) {
            ReceiverScreen(
                navigateToSender = {
                    coroutineScope.launch {
                        dataStoreManager.saveLastPage(NavigationItem.Sender.route)
                    }
                    navController.navigate(NavigationItem.Sender.route) {
                        popUpTo(NavigationItem.Receiver.route) {
                            inclusive = true
                        }
                    }
                },
                navigateToSchedules = {
                    coroutineScope.launch {
                        dataStoreManager.saveLastPage(NavigationItem.Schedules.route)
                    }
                    navController.navigate("${NavigationItem.Schedules.route}?mode=receiver")
                },
                navigateToLogs = {
                    coroutineScope.launch {
                        dataStoreManager.saveLastPage(NavigationItem.Logs.route)
                    }
                    navController.navigate(NavigationItem.Logs.route)
                }
            )
        }

        composable(
            route = "${NavigationItem.Schedules.route}?mode={mode}&serverId={serverId}",
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = "receiver"
                },
                navArgument("serverId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "receiver"
            val serverId = backStackEntry.arguments?.getString("serverId")
            
            SchedulesScreen(
                mode = mode,
                serverId = serverId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            NavigationItem.Logs.route,
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) {
            LogScreen {
                navController.popBackStack()
            }
        }

    }
}


sealed class NavigationItem(val route: String) {
    object Sender : NavigationItem("sender")
    object Receiver : NavigationItem("receiver")
    object Schedules : NavigationItem("schedules")
    object Logs : NavigationItem("logs")
}