package ch.srgssr.pillarbox.backend.adapter.integrationlayer

import ch.srgssr.pillarbox.backend.domain.model.Chapter
import ch.srgssr.pillarbox.backend.domain.model.DrmConfig
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.MediaMetadata
import ch.srgssr.pillarbox.backend.domain.model.MediaSource
import ch.srgssr.pillarbox.backend.domain.model.SubtitleTrack
import ch.srgssr.pillarbox.backend.domain.model.TimeRange
import ch.srgssr.pillarbox.backend.domain.playback.DrmSystems
import ch.srgssr.pillarbox.backend.domain.playback.MimeTypes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val supportedStreamings = listOf("HLS", "DASH", "PROGRESSIVE")

private val drmKeySystems =
  mapOf(
    "FAIRPLAY" to DrmSystems.FAIRPLAY,
    "WIDEVINE" to DrmSystems.WIDEVINE,
    "PLAYREADY" to DrmSystems.PLAYREADY,
  )

/**
 * Converts an Integration Layer media composition into a [Media] item.
 *
 * Maps the main chapter's metadata, one source per supported streaming
 * method (preferring DVR and HD variants), DRM configurations, the chapters
 * cut from the main chapter, and VTT subtitle tracks. Blocked segments and
 * credits become time ranges. 360° streams set a
 * monoscopic viewport. The token type and the block reason, which have no
 * standard field, go to the custom data.
 *
 * @return The mapped media, or null when the composition has no chapter.
 */
fun MediaComposition.toMedia(): Media? {
  val chapter = mainChapter ?: return null
  val resources = chapter.selectResources()

  return Media(
    id = chapter.urn,
    sources = resources.map { it.toMediaSource(chapter.mediaType) },
    metadata =
      MediaMetadata(
        title = chapter.title,
        subtitle = show?.title,
        description = chapter.description?.takeIf { it.isNotBlank() } ?: chapter.lead,
        posterUrl = chapter.imageUrl,
        seasonNumber = episode?.seasonNumber,
        episodeNumber = episode?.number,
        viewport = if (resources.any { it.presentation == "VIDEO_360" }) "MONOSCOPIC" else null,
        subtitles = chapter.toSubtitleTracks().ifEmpty { null },
        chapters = chaptersOf(chapter).ifEmpty { null },
        timeRanges = chapter.toTimeRanges().ifEmpty { null },
        customData = customDataOf(chapter, resources),
      ),
    expiresAt = chapter.validTo,
  )
}

/**
 * Selects one resource per supported streaming method, preferring DVR and HD variants.
 *
 * @return The selected resources, in [supportedStreamings] order.
 */
private fun CompositionChapter.selectResources(): List<Resource> =
  supportedStreamings.mapNotNull { streaming ->
    resourceList
      .filter { it.streaming == streaming }
      .maxWithOrNull(compareBy({ it.dvr }, { it.quality == "HD" }))
  }

/**
 * Builds the custom data from the Integration Layer fields that have no standard field.
 *
 * @param chapter The imported chapter, providing the block reason.
 * @param resources The selected resources, providing the token type.
 * @return The custom data, or null when there is nothing to add.
 */
private fun customDataOf(
  chapter: CompositionChapter,
  resources: List<Resource>,
): JsonObject? =
  buildJsonObject {
    resources.firstNotNullOfOrNull { it.tokenType }?.let { put("tokenType", JsonPrimitive(it)) }
    chapter.blockReason?.let { put("blockReason", JsonPrimitive(it)) }
  }.takeIf { it.isNotEmpty() }

private fun Resource.toMediaSource(mediaType: String?): MediaSource =
  MediaSource(
    url = url,
    type =
      when {
        live && dvr -> "DVR"
        live -> "LIVE"
        else -> "ON-DEMAND"
      },
    mimeType = mimeType ?: defaultMimeType(mediaType),
    drmConfigs = drmList.mapNotNull { it.toDrmConfig() },
  )

private fun Resource.defaultMimeType(mediaType: String?): String? =
  when (streaming) {
    "HLS" -> MimeTypes.HLS_LEGACY
    "DASH" -> MimeTypes.DASH
    "PROGRESSIVE" -> if (mediaType == "AUDIO") MimeTypes.M4A else MimeTypes.MP4
    else -> null
  }

private fun Drm.toDrmConfig(): DrmConfig? =
  drmKeySystems[type]?.let { keySystem ->
    DrmConfig(
      keySystem = keySystem,
      licenseUrl = licenseUrl,
      certificateUrl = certificateUrl,
    )
  }

/**
 * Maps the chapters cut from [fullLength] to [Chapter] items.
 *
 * @param fullLength The full-length chapter whose chapters are mapped.
 * @return The chapters positioned within [fullLength], empty when it has none.
 */
private fun MediaComposition.chaptersOf(fullLength: CompositionChapter): List<Chapter> =
  chapterList
    .filter { it.fullLengthUrn == fullLength.urn }
    .mapNotNull { chapter ->
      Chapter(
        identifier = chapter.urn,
        title = chapter.title,
        posterUrl = chapter.imageUrl,
        startTime = chapter.fullLengthMarkIn ?: return@mapNotNull null,
        endTime = chapter.fullLengthMarkOut ?: return@mapNotNull null,
      )
    }

/**
 * Maps the blocked segments and the credits of the chapter to [TimeRange] items.
 *
 * @return The `BLOCKED` ranges followed by the credits ranges, empty when there are none.
 */
private fun CompositionChapter.toTimeRanges(): List<TimeRange> =
  segmentList
    .filter { it.blockReason != null }
    .mapNotNull { segment ->
      TimeRange(
        startTime = segment.markIn ?: return@mapNotNull null,
        endTime = segment.markOut ?: return@mapNotNull null,
        type = "BLOCKED",
      )
    } +
    timeIntervalList.map { TimeRange(startTime = it.markIn, endTime = it.markOut, type = it.type) }

private fun CompositionChapter.toSubtitleTracks(): List<SubtitleTrack> =
  subtitleList
    .filter { it.format == "VTT" }
    .mapNotNull { subtitle ->
      val locale = subtitle.locale ?: return@mapNotNull null
      SubtitleTrack(
        label = subtitle.language ?: locale,
        kind = if (subtitle.type == "SDH") "captions" else "subtitles",
        language = locale,
        url = subtitle.url,
      )
    }
