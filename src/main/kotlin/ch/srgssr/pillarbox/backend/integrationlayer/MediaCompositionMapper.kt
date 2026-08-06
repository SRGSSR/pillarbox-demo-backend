package ch.srgssr.pillarbox.backend.integrationlayer

import ch.srgssr.pillarbox.backend.domain.model.Chapter
import ch.srgssr.pillarbox.backend.domain.model.DrmConfig
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.MediaMetadata
import ch.srgssr.pillarbox.backend.domain.model.MediaSource
import ch.srgssr.pillarbox.backend.domain.model.SubtitleTrack
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.DrmSystems
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.MimeTypes

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
 * method (preferring DVR and HD variants), DRM configurations, segments
 * as chapters, and VTT subtitle tracks.
 *
 * @return The mapped media, or null when the composition has no chapter.
 */
fun MediaComposition.toMedia(): Media? {
  val chapter = mainChapter ?: return null

  return Media(
    id = chapter.urn,
    sources = chapter.toSources(),
    metadata =
      MediaMetadata(
        title = chapter.title,
        subtitle = show?.title,
        description = chapter.description?.takeIf { it.isNotBlank() } ?: chapter.lead,
        posterUrl = chapter.imageUrl,
        seasonNumber = episode?.seasonNumber,
        episodeNumber = episode?.number,
        subtitles = chapter.toSubtitleTracks().ifEmpty { null },
        chapters = chapter.toChapters().ifEmpty { null },
      ),
  )
}

private fun CompositionChapter.toSources(): List<MediaSource> =
  supportedStreamings.mapNotNull { streaming ->
    resourceList
      .filter { it.streaming == streaming }
      .maxWithOrNull(compareBy({ it.dvr }, { it.quality == "HD" }))
      ?.toMediaSource(mediaType)
  }

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

private fun CompositionChapter.toChapters(): List<Chapter> =
  segmentList
    .filter { it.urn != urn }
    .mapNotNull { segment ->
      Chapter(
        identifier = segment.urn,
        title = segment.title,
        posterUrl = segment.imageUrl,
        startTime = segment.markIn ?: return@mapNotNull null,
        endTime = segment.markOut ?: return@mapNotNull null,
      )
    }

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
