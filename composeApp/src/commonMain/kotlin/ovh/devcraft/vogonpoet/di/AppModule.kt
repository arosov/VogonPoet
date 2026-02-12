package ovh.devcraft.vogonpoet.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.domain.BackendRepository
import ovh.devcraft.vogonpoet.infrastructure.BackendRepositoryImpl
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.infrastructure.BabelfishClient as BabelfishClientImpl

val appModule =
    module {
        single<CoroutineScope> {
            CoroutineScope(Dispatchers.Default + SupervisorJob())
        } bind (CoroutineScope::class) onClose {
            it?.cancel()
        }

        single<BabelfishClient> {
            BabelfishClientImpl(backendRepository = get(), scope = get())
        } onClose {
            it?.close()
        }

        single<BackendRepository> {
            BackendRepositoryImpl
        }

        viewModelOf(::MainViewModel)
    }
