package hu.krafcsikgergo.wakeonwan

import android.app.Application
import hu.krafcsikgergo.wakeonwan.common.services.LogManager
import hu.krafcsikgergo.wakeonwan.common.services.LogManagerImpl
import hu.krafcsikgergo.wakeonwan.common.ui.screens.LogViewModel
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.DataStoreManagerImpl
import hu.krafcsikgergo.wakeonwan.services.receiver.SSHManager
import hu.krafcsikgergo.wakeonwan.services.receiver.SSHManagerImpl
import hu.krafcsikgergo.wakeonwan.services.receiver.ScheduleManager
import hu.krafcsikgergo.wakeonwan.services.receiver.ScheduleManagerImpl
import hu.krafcsikgergo.wakeonwan.services.receiver.WakeOnLanService
import hu.krafcsikgergo.wakeonwan.services.receiver.WakeOnLanServiceImpl
import hu.krafcsikgergo.wakeonwan.ui.screens.ReceiverViewModel
import hu.krafcsikgergo.wakeonwan.ui.screens.SchedulesViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Application class for the Wake on WAN receiver app.
 * Runs the local Ktor server and manages Wake-on-LAN / SSH / scheduling.
 */
class ReceiverApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@ReceiverApplication)

            modules(
                module {
                    singleOf(::DataStoreManagerImpl).bind<DataStoreManager>()
                    singleOf(::LogManagerImpl).bind<LogManager>()
                    singleOf(::WakeOnLanServiceImpl).bind<WakeOnLanService>()
                    singleOf(::SSHManagerImpl).bind<SSHManager>()
                    singleOf(::ScheduleManagerImpl).bind<ScheduleManager>()

                    viewModelOf(::ReceiverViewModel)
                    viewModelOf(::SchedulesViewModel)
                    viewModelOf(::LogViewModel)
                }
            )
        }
    }
}
