package ovh.devcraft.vogonpoet.infrastructure

import io.github.arosov.kwtransport.Endpoint

fun interface EndpointProvider {
    suspend fun createClientEndpoint(): BabelfishEndpoint
}