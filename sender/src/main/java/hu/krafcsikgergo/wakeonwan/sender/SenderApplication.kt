package hu.krafcsikgergo.wakeonwan.sender

import android.app.Application
import hu.krafcsikgergo.wakeonwan.common.services.LogManager
import hu.krafcsikgergo.wakeonwan.common.services.LogManagerImpl
import hu.krafcsikgergo.wakeonwan.common.ui.screens.LogViewModel
import hu.krafcsikgergo.wakeonwan.sender.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.sender.services.DataStoreManagerImpl
import hu.krafcsikgergo.wakeonwan.sender.services.NetworkRepository
import hu.krafcsikgergo.wakeonwan.sender.services.NetworkRepositoryImpl
import hu.krafcsikgergo.wakeonwan.sender.ui.screens.SchedulesViewModel
import hu.krafcsikgergo.wakeonwan.sender.ui.screens.SenderViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Application class for the Wake on WAN sender app.
 * Sends wake-up / shutdown / schedule requests to a remote receiver over HTTP.
 */
class SenderApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@SenderApplication)

            modules(
                module {
                    single<HttpClient> {
                        HttpClient(Android) {
                            install(ContentNegotiation) {
                                json()
                            }
                        }
                    }

                    singleOf(::DataStoreManagerImpl).bind<DataStoreManager>()
                    singleOf(::LogManagerImpl).bind<LogManager>()
                    singleOf(::NetworkRepositoryImpl).bind<NetworkRepository>()

                    viewModelOf(::SenderViewModel)
                    viewModelOf(::SchedulesViewModel)
                    viewModelOf(::LogViewModel)
                }
            )
        }
    }
}
