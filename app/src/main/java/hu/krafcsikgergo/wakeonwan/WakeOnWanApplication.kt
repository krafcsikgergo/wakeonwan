package hu.krafcsikgergo.wakeonwan

import android.app.Application
import hu.krafcsikgergo.wakeonwan.services.sender.NetworkRepository
import hu.krafcsikgergo.wakeonwan.services.receiver.WakeOnLanService
import hu.krafcsikgergo.wakeonwan.services.sender.NetworkRepositoryImpl
import hu.krafcsikgergo.wakeonwan.services.receiver.WakeOnLanServiceImpl
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.DataStoreManagerImpl
import hu.krafcsikgergo.wakeonwan.services.LogManager
import hu.krafcsikgergo.wakeonwan.services.LogManagerImpl
import hu.krafcsikgergo.wakeonwan.services.receiver.SSHManager
import hu.krafcsikgergo.wakeonwan.services.receiver.SSHManagerImpl
import hu.krafcsikgergo.wakeonwan.services.receiver.ScheduleManager
import hu.krafcsikgergo.wakeonwan.services.receiver.ScheduleManagerImpl
import hu.krafcsikgergo.wakeonwan.ui.screens.ReceiverViewModel
import hu.krafcsikgergo.wakeonwan.ui.screens.SchedulesViewModel
import hu.krafcsikgergo.wakeonwan.ui.screens.SenderViewModel
import hu.krafcsikgergo.wakeonwan.ui.screens.LogViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Application class for WakeOnWAN app with Koin dependency injection setup.
 * Initializes Koin DI container with all required modules.
 */
class WakeOnWanApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            // Use Android logger for debug information
            androidLogger()

            // Provide Android context
            androidContext(this@WakeOnWanApplication)

            // Load all application modules
            modules(
                module {
                    // Network Dependencies
                    single<HttpClient> {
                        HttpClient(Android) {
                            install(ContentNegotiation) {
                                json()
                            }
                        }
                    }

                    // Service Dependencies
                    singleOf(::DataStoreManagerImpl).bind<DataStoreManager>()
                    singleOf(::LogManagerImpl).bind<LogManager>()
                    singleOf(::NetworkRepositoryImpl).bind<NetworkRepository>()
                    singleOf(::WakeOnLanServiceImpl).bind<WakeOnLanService>()
                    singleOf(::SSHManagerImpl).bind<SSHManager>()
                    singleOf(::ScheduleManagerImpl).bind<ScheduleManager>()

                    // ViewModels
                    viewModelOf(::SenderViewModel)
                    viewModelOf(::ReceiverViewModel)
                    viewModelOf(::SchedulesViewModel)
                    viewModelOf(::LogViewModel)
                }
            )
        }
    }
}