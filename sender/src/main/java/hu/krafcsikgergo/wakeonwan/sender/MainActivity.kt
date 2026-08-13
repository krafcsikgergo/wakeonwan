package hu.krafcsikgergo.wakeonwan.sender

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
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import hu.krafcsikgergo.wakeonwan.common.ui.screens.LogScreen
import hu.krafcsikgergo.wakeonwan.common.ui.theme.WakeOnWANTheme
import hu.krafcsikgergo.wakeonwan.sender.ui.screens.SchedulesScreen
import hu.krafcsikgergo.wakeonwan.sender.ui.screens.SenderScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        installSplashScreen()

        setContent {
            // Remove when https://issuetracker.google.com/issues/364713509 is fixed
            LaunchedEffect(isSystemInDarkTheme()) { enableEdgeToEdge() }
            WakeOnWANTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SenderNavigation()
                }
            }
        }
    }
}

private object Routes {
    const val Sender = "sender"
    const val Schedules = "schedules"
    const val Logs = "logs"
}

@Composable
private fun SenderNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Sender
    ) {
        composable(
            Routes.Sender,
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) {
            SenderScreen(
                navigateToLogs = { navController.navigate(Routes.Logs) },
                navigateToSchedules = { serverId ->
                    navController.navigate("${Routes.Schedules}/$serverId")
                }
            )
        }

        composable(
            route = "${Routes.Schedules}/{serverId}",
            arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId") ?: return@composable
            SchedulesScreen(
                serverId = serverId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.Logs,
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) {
            LogScreen {
                navController.popBackStack()
            }
        }
    }
}
