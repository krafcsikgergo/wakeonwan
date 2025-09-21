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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hu.krafcsikgergo.wakeonwan.ui.screens.ReceiverScreen
import hu.krafcsikgergo.wakeonwan.ui.screens.SchedulesScreen
import hu.krafcsikgergo.wakeonwan.ui.screens.SenderScreen
import hu.krafcsikgergo.wakeonwan.ui.theme.WakeOnWANTheme
import org.koin.core.component.KoinComponent

class MainActivity : ComponentActivity(), KoinComponent {

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
                    NavHost()
                }
            }
        }
    }
}

@Composable
fun NavHost(
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
            SenderScreen(
                navigateToReceiver = {
                    navController.navigate(NavigationItem.Receiver.route) {
                        popUpTo(NavigationItem.Sender.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(
            NavigationItem.Receiver.route,
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) {
            ReceiverScreen(
                navigateToSender = {
                    navController.navigate(NavigationItem.Sender.route) {
                        popUpTo(NavigationItem.Receiver.route) {
                            inclusive = true
                        }
                    }
                },
                navigateToSchedules = {
                    navController.navigate(NavigationItem.Schedules.route)
                }
            )
        }

        composable(
            NavigationItem.Schedules.route,
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 10)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 10)) }) {
            SchedulesScreen {
                navController.popBackStack()
            }
        }

    }
}


sealed class NavigationItem(val route: String) {
    object Sender : NavigationItem("sender")
    object Receiver : NavigationItem("receiver")
    object Schedules : NavigationItem("schedules")
}