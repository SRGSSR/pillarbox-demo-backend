package ch.srgssr.pillarbox.backend.adapter.integrationlayer

import io.ktor.server.config.ApplicationConfig

/**
 * Configuration parameters for the SRG SSR Integration Layer client.
 *
 * @property baseUrl The base URL of the Integration Layer service.
 */
data class IntegrationLayerConfig(
  val baseUrl: String,
)

/**
 * Extracts [IntegrationLayerConfig] from the application's configuration file.
 *
 * @return A populated [IntegrationLayerConfig] instance.
 */
fun ApplicationConfig.toIntegrationLayerConfig(): IntegrationLayerConfig =
  IntegrationLayerConfig(
    baseUrl = property("integrationLayer.baseUrl").getString(),
  )
