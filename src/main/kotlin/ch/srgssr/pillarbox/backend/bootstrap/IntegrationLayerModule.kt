package ch.srgssr.pillarbox.backend.bootstrap

import ch.srgssr.pillarbox.backend.adapter.integrationlayer.IntegrationLayerClient
import ch.srgssr.pillarbox.backend.adapter.integrationlayer.IntegrationLayerConfig
import ch.srgssr.pillarbox.backend.adapter.integrationlayer.IntegrationLayerImportSource
import ch.srgssr.pillarbox.backend.adapter.integrationlayer.toIntegrationLayerConfig
import ch.srgssr.pillarbox.backend.application.media.ImportMediaFromUrn
import ch.srgssr.pillarbox.backend.domain.port.MediaImportSource
import io.ktor.server.config.ApplicationConfig
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Defines the Koin module for the Integration Layer integration.
 *
 * Provides the [IntegrationLayerConfig] extracted from the application
 * configuration, an [IntegrationLayerClient] backed by the shared HTTP client,
 * the [IntegrationLayerImportSource] bound as the [MediaImportSource] port, and
 * the [ImportMediaFromUrn] use case built on it.
 *
 * @return A Koin module containing the Integration Layer definitions.
 */
fun integrationLayerModule() =
  module {
    single { get<ApplicationConfig>().toIntegrationLayerConfig() }
    single { IntegrationLayerClient(httpClient = get(), config = get()) }
    single { IntegrationLayerImportSource(client = get()) } bind MediaImportSource::class
    single { ImportMediaFromUrn(importSource = get(), mediaCatalog = get()) }
  }
