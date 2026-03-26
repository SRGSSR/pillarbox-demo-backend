package ch.srgssr.pillarbox.backend.entrypoint.web.dto

import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.MediaMetadata
import ch.srgssr.pillarbox.backend.domain.model.MediaSource
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Data Transfer Object (V1) representing a media resource in the admin web entry point.
 *
 * This class provides the full state of a [Media] entity. Unlike [PlayerMediaResponseV1],
 * which is optimized for playback, this response contains the complete, unfiltered lists
 * of sources and DRM configurations.
 *
 * It is typically used by management interfaces or the Admin Panel to display or
 * retrieve the full configuration of a media resource.
 *
 * @property id The unique identifier for the media resource.
 * @property tags A list of keywords or labels used for categorization or filtering.
 * @property sources The complete collection of all available streaming endpoints.
 * @property metadata Detailed contextual information including titles, chapters, and markers.
 * @property deleted Whether the media has been deleted or not.
 * @property createdAt The time when the media was created.
 * @property lastModified The time when the media was last modified.
 */
@Serializable
data class MediaResponseV1(
  val id: String,
  val tags: List<String> = emptyList(),
  val sources: List<MediaSource> = emptyList(),
  val metadata: MediaMetadata,
  val deleted: Boolean,
  val createdAt: Instant,
  val lastModified: Instant,
)

/**
 * Maps the internal [Media] domain model to the [MediaResponseV1] DTO.
 *
 * Use this extension to prepare domain data for the admin web entry point.
 *
 * @return A [MediaResponseV1] containing the domain model's data.
 */
fun Media.toMediaResponseV1() =
  MediaResponseV1(
    id = this.id,
    tags = this.tags,
    sources = this.sources,
    metadata = this.metadata,
    deleted = this.deleted,
    createdAt = this.createdAt,
    lastModified = this.lastModified,
  )
