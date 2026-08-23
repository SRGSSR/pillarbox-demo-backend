package ch.srgssr.pillarbox.backend.adapter.integrationlayer

import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.port.MediaImportSource
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger

/**
 * [MediaImportSource] adapter backed by the SRG SSR Integration Layer.
 *
 * Fetches the media composition of a URN and maps it to the [Media] domain model.
 *
 * @property client The Integration Layer client used to fetch compositions.
 */
class IntegrationLayerImportSource(
  private val client: IntegrationLayerClient,
) : MediaImportSource {
  companion object {
    val logger = logger()
  }

  /**
   * Fetches and maps the media identified by [urn].
   *
   * @param urn The URN of the media to import.
   * @return The mapped media, or `null` when the URN is unknown, the composition has
   *         no chapter, or the Integration Layer is unreachable.
   */
  override suspend fun findByUrn(urn: String): Media? =
    runCatching { client.findMediaComposition(urn) }
      .onFailure { logger.info { "Failed to fetch media composition for URN $urn: ${it.message}" } }
      .getOrNull()
      ?.toMedia()
}
