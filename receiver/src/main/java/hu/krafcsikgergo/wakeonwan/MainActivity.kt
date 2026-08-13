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
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hu.krafcsikgergo.wakeonwan.common.ui.screens.LogScreen
import hu.krafcsikgergo.wakeonwan.common.ui.theme.WakeOnWANTheme
import hu.krafcsikgergo.wakeonwan.ui.screens.ReceiverScreen
import hu.krafcsikgergo.wakeonwan.ui.screens.SchedulesScreen

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
                    ReceiverNavigation()
                }
            }
        }
    }
}

private object Routes {
    const val Receiver = "receiver"
    const val Schedules = "schedules"
    const val Logs = "logs"
}

@Composable
private fun ReceiverNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Receiver
    ) {
        composable(
            Routes.Receiver,
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) {
            ReceiverScreen(
                navigateToSchedules = { navController.navigate(Routes.Schedules) },
                navigateToLogs = { navController.navigate(Routes.Logs) }
            )
        }

        composable(
            Routes.Schedules,
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) {
            SchedulesScreen(
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
