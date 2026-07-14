package ch.srgssr.pillarbox.backend.integrationlayer

import io.ktor.server.config.ApplicationConfig
import org.koin.dsl.module

/**
 * Defines the Koin module for the Integration Layer integration.
 *
 * Provides the [IntegrationLayerConfig] extracted from the application
 * configuration and an [IntegrationLayerClient] backed by the shared HTTP client.
 *
 * @return A Koin module containing the Integration Layer definitions.
 */
fun integrationLayerModule() =
  module {
    single { get<ApplicationConfig>().toIntegrationLayerConfig() }
    single { IntegrationLayerClient(httpClient = get(), config = get()) }
  }
