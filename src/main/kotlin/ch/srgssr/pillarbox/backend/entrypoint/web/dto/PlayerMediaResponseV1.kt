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
 * Implements source selection logic: filters by MIME type and DRM compatibility,
 * then picks the best match according to the caller's priority lists.
 *
 * @param mimeTypes Prioritized list of accepted MIME types (e.g. "application/dash+xml").
 *                  A source must match at least one entry to be considered.
 * @param keySystems Prioritized list of accepted DRM key systems (e.g. "com.widevine.alpha").
 *                   When empty, both protected and unprotected sources are eligible.
 *
 * @return A player-optimized response containing only the best-matching stream and DRM info.
 */
fun Media.toPlayerResponse(
  mimeTypes: List<String> = emptyList(),
  keySystems: List<String> = emptyList(),
): PlayerMediaResponseV1 {
  val selectedSource =
    sources
      .filter { it.matchesMimeType(mimeTypes) && it.matchesAnyKeySystem(keySystems) }
      .map { it.retainingOnlyMatchingDrm(keySystems) }
      .sortedWith(preferredSourceOrder(mimeTypes, keySystems))
      .firstOrNull()

  return PlayerMediaResponseV1(
    identifier = id,
    title = metadata.title,
    subtitle = metadata.subtitle,
    description = metadata.description,
    posterUrl = metadata.posterUrl,
    seasonNumber = metadata.seasonNumber,
    episodeNumber = metadata.episodeNumber,
    viewport = metadata.viewport,
    source = selectedSource?.toPlayerMediaSourceV1(),
    drm = selectedSource?.preferredDrm(keySystems),
    subtitles = metadata.subtitles,
    chapters = metadata.chapters,
    timeRanges = metadata.timeRanges,
    customData = metadata.customData,
  )
}

private fun MediaSource.matchesMimeType(mimeTypes: List<String>): Boolean =
  mimeTypes.any { mimeType?.equals(it, ignoreCase = true) == true }

private fun DrmConfig.matchesKeySystem(keySystems: List<String>): Boolean =
  keySystems.any { this.keySystem.equals(it, ignoreCase = true) }

/**
 * A source is DRM-compatible when:
 * - the source is unprotected, OR
 * - at least one of its DRM configs matches a requested key system.
 */
private fun MediaSource.matchesAnyKeySystem(keySystems: List<String>): Boolean =
  drmConfigs.isEmpty() || drmConfigs.any { it.matchesKeySystem(keySystems) }

/** Returns a copy of this source keeping only DRM configs that match a requested key system. */
private fun MediaSource.retainingOnlyMatchingDrm(keySystems: List<String>): MediaSource =
  copy(drmConfigs = drmConfigs.filter { it.matchesKeySystem(keySystems) })

/**
 * Ordering rules (ascending = more preferred):
 * 1. Protected sources before unprotected ones (when a key system was requested).
 * 2. Lower MIME type index = higher priority in [mimeTypes].
 * 3. Lower key system index = higher priority in [keySystems].
 */
private fun preferredSourceOrder(
  mimeTypes: List<String>,
  keySystems: List<String>,
): Comparator<MediaSource> =
  compareBy(
    { if (it.drmConfigs.isEmpty()) 1 else 0 },
    { mimeTypes.indexOfFirst { mt -> it.mimeType?.equals(mt, ignoreCase = true) == true } },
    { it.bestDrmPriority(keySystems) },
  )

/** Returns the best (lowest) key-system priority index across all DRM configs, or MAX if none. */
private fun MediaSource.bestDrmPriority(keySystems: List<String>): Int =
  drmConfigs.minOfOrNull { drm ->
    keySystems.indexOfFirst { ks -> drm.keySystem.equals(ks, ignoreCase = true) }
  } ?: Int.MAX_VALUE

/** Returns the first DRM config whose key system appears in [keySystems], respecting their order. */
private fun MediaSource.preferredDrm(keySystems: List<String>): DrmConfig? =
  keySystems.firstNotNullOfOrNull { ks ->
    drmConfigs.find { drm -> drm.keySystem.equals(ks, ignoreCase = true) }
  }

/**
 * Maps the internal [Media] domain model to the [MediaResponseV1] DTO.
 *
 * Use this extension to prepare domain data for the admin web entry point.
 *
 * @return A [MediaResponseV1] containing the domain model's data.
 */
fun MediaSource.toPlayerMediaSourceV1() =
  PlayerMediaResponseV1.MediaSource(
    url = url,
    type = type,
    mimeType = mimeType,
    videoFragmentFormat = videoFragmentFormat,
    audioFragmentFormat = audioFragmentFormat,
  )
