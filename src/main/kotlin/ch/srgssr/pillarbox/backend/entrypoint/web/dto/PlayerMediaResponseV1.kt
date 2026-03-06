package ch.srgssr.pillarbox.backend.entrypoint.web.dto

import ch.srgssr.pillarbox.backend.domain.model.Chapter
import ch.srgssr.pillarbox.backend.domain.model.DrmConfig
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.MediaSource
import ch.srgssr.pillarbox.backend.domain.model.SubtitleTrack
import ch.srgssr.pillarbox.backend.domain.model.TimeRange
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Data Transfer Object (V1) optimized for media playback.
 *
 * This class represents client-specific view of a media resource.
 *
 * Unlike [MediaResponseV1], which contains all available options, this response
 * provides a single [source] and [drm] configuration selected based on the player's
 * requirements.
 *
 * @property identifier The unique identifier for the media.
 * @property title The primary title for display in the player.
 * @property subtitle A secondary title or summary for display.
 * @property description A detailed description of the content.
 * @property posterUrl URL to the artwork/thumbnail for the player UI.
 * @property seasonNumber The season index, if applicable.
 * @property episodeNumber The episode index, if applicable.
 * @property viewport The preferred aspect ratio or display mode.
 * @property source The specific stream selected for this playback session.
 * @property drm The specific DRM configuration selected for this playback session.
 * @property subtitles The list of available subtitle or closed caption tracks.
 * @property chapters Timed markers for player navigation.
 * @property timeRanges Specific playback regions (e.g., intro, credits, blocked segments).
 * @property customData Flexible JSON object for implementation-specific player data.
 */
@Serializable
data class PlayerMediaResponseV1(
  val identifier: String?,
  val title: String?,
  val subtitle: String?,
  val description: String?,
  val posterUrl: String?,
  val seasonNumber: Int?,
  val episodeNumber: Int?,
  val viewport: String?,
  val source: MediaSource?,
  val drm: DrmConfig?,
  val subtitles: List<SubtitleTrack>?,
  val chapters: List<Chapter>?,
  val timeRanges: List<TimeRange>?,
  val customData: JsonObject?,
)

/**
 * Transforms a [Media] domain model into a [PlayerMediaResponseV1].
 *
 * This function implements the selection logic to filter through multiple available
 * sources and DRM configurations.
 *
 * @param mimeType The preferred MIME type (e.g., "application/dash+xml")
 * @param keySystem The preferred DRM system (e.g., "com.widevine.alpha")
 *
 * @return A player-optimized response containing only the relevant stream and DRM info.
 */
fun Media.toPlayerResponse(
  mimeType: String?,
  keySystem: String?,
): PlayerMediaResponseV1 {
  val selectedSource =
    sources.firstOrNull { source ->
      source.mimeType?.equals(mimeType, ignoreCase = true) == true
    }

  val selectedDrm =
    selectedSource?.drmConfigs?.firstOrNull {
      it.keySystem.equals(keySystem, ignoreCase = true)
    }

  return PlayerMediaResponseV1(
    identifier = id,
    title = metadata.title,
    subtitle = metadata.subtitle,
    description = metadata.description,
    posterUrl = metadata.posterUrl,
    seasonNumber = metadata.seasonNumber,
    episodeNumber = metadata.episodeNumber,
    viewport = metadata.viewport,
    source = selectedSource,
    drm = selectedDrm,
    subtitles = metadata.subtitles,
    chapters = metadata.chapters,
    timeRanges = metadata.timeRanges,
    customData = metadata.customData,
  )
}
