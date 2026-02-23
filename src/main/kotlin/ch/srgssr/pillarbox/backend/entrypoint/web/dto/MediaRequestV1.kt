package ch.srgssr.pillarbox.backend.entrypoint.web.dto

import ch.srgssr.pillarbox.backend.domain.model.DrmConfig
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.MediaMetadata
import ch.srgssr.pillarbox.backend.domain.model.MediaSource
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (V1) representing a media request from the admin web entry point.
 *
 * This class acts as the external contract for the Pillarbox API. It decouples the
 * public API schema from the internal domain logic, allowing for versioned
 * evolution of the media structure.
 *
 * @property id The unique identifier for the media resource.
 * @property tags A list of keywords or labels used for categorization or filtering.
 * @property sources The collection of streaming endpoints available for this media.
 * @property drmConfigs Digital Rights Management configurations for protected content.
 * @property metadata Contextual information including titles, chapters, and time ranges.
 */
@Serializable
data class MediaRequestV1(
  val id: String,
  val tags: List<String> = emptyList(),
  val sources: List<MediaSource> = emptyList(),
  val drmConfigs: List<DrmConfig> = emptyList(),
  val metadata: MediaMetadata,
) {
  /**
   * Maps the [MediaRequestV1] DTO to the internal [Media] domain model.
   *
   * Use this method to pass the validated request data into the service or
   * repository layers where domain-specific logic is applied.
   *
   * @return A [Media] instance populated with the request's data.
   */
  fun toMedia() =
    Media(
      id = id,
      tags = tags,
      sources = sources,
      drmConfigs = drmConfigs,
      metadata = metadata,
    )
}
