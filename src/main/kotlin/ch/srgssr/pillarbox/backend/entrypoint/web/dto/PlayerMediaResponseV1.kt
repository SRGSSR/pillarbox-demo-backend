package ch.srgssr.pillarbox.backend.entrypoint.web.dto

import ch.srgssr.pillarbox.backend.domain.model.Chapter
import ch.srgssr.pillarbox.backend.domain.model.DrmConfig
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.MediaSource
import ch.srgssr.pillarbox.backend.domain.model.SubtitleTrack
import ch.srgssr.pillarbox.backend.domain.model.TimeRange
import ch.srgssr.pillarbox.backend.entrypoint.web.service.MediaSourceSelector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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
) {
  @Serializable
  data class MediaSource(
    val url: String,
    val type: String? = null,
    val mimeType: String? = null,
    val videoFragmentFormat: String? = null,
    val audioFragmentFormat: String? = null,
  )
}

/**
 * Transforms a [Media] domain model into a [PlayerMediaResponseV1].
 *
 * Source selection is fully delegated to [selector]. This function only
 * handles the mapping from the selected source to the response shape.
 *
 * @param selector The [MediaSourceSelector] that encapsulates the client's stream-type and
 *                 DRM capabilities.
 * @return A player-optimized response containing only the best-matching stream and DRM info.
 */
fun Media.toPlayerResponse(selector: MediaSourceSelector): PlayerMediaResponseV1 {
  val selection = selector.select(sources)
  return PlayerMediaResponseV1(
    identifier = id,
    title = metadata.title,
    subtitle = metadata.subtitle,
    description = metadata.description,
    posterUrl = metadata.posterUrl,
    seasonNumber = metadata.seasonNumber,
    episodeNumber = metadata.episodeNumber,
    viewport = metadata.viewport,
    source = selection?.source?.toPlayerMediaSourceV1(),
    drm = selection?.drm,
    subtitles = metadata.subtitles,
    chapters = metadata.chapters,
    timeRanges = metadata.timeRanges,
    customData =
      buildJsonObject {
        metadata.customData?.forEach { (k, v) -> put(k, v) }
        expiresAt?.let { put("expiresAt", JsonPrimitive(it.toEpochMilliseconds())) }
      }.takeIf { it.isNotEmpty() },
  )
}

/**
 * Maps the domain [MediaSource] to the player-specific [PlayerMediaResponseV1.MediaSource] DTO.
 */
fun MediaSource.toPlayerMediaSourceV1() =
  PlayerMediaResponseV1.MediaSource(
    url = url,
    type = type,
    mimeType = mimeType,
    videoFragmentFormat = videoFragmentFormat,
    audioFragmentFormat = audioFragmentFormat,
  )
