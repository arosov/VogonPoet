package ovh.devcraft.vogonpoet.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ovh.devcraft.vogonpoet.domain.BabelfishClient
import ovh.devcraft.vogonpoet.infrastructure.KwBabelfishClient
import ovh.devcraft.vogonpoet.presentation.MainViewModel

val appModule =
    module {
        single<CoroutineScope> { CoroutineScope(Dispatchers.Default + SupervisorJob()) }

        single<BabelfishClient> {
            KwBabelfishClient(scope = get())
        }

        viewModelOf(::MainViewModel)
    }
