package ch.srgssr.pillarbox.backend.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents a complete media object with its associated streams, DRM, and metadata.
 *
 * @property id The unique identifier for the media.
 * @property tags A list of labels or categories associated with the media.
 * @property sources The list of available playback sources (streams).
 * @property drmConfigs The DRM configurations available for this media.
 * @property metadata Descriptive information for display and playback.
 */
@Serializable
data class Media(
  val id: String,
  val tags: List<String> = emptyList(),
  val sources: List<MediaSource> = emptyList(),
  val drmConfigs: List<DrmConfig> = emptyList(),
  val metadata: MediaMetadata,
)

/**
 * Information about a specific streamable source.
 *
 * @property url The URL of the media stream.
 * @property type The type of the source (e.g., "ON-DEMAND", "LIVE").
 * @property mimeType The MIME type of the content.
 * @property videoFragmentFormat The format used for video segments.
 * @property audioFragmentsFormat The format used for audio segments.
 */
@Serializable
data class MediaSource(
  val url: String,
  val type: String? = null,
  val mimeType: String? = null,
  val videoFragmentFormat: String? = null,
  val audioFragmentsFormat: String? = null,
)

/**
 * Descriptive and structural metadata for the media.
 *
 * @property title The primary title of the media.
 * @property subtitle A secondary title or summary.
 * @property description A detailed description of the content.
 * @property posterUrl URL to an image representing the media.
 * @property seasonNumber The season index if the media is part of a series.
 * @property episodeNumber The episode index within a season.
 * @property viewport The preferred aspect ratio or display mode.
 * @property subtitles A list of available external subtitle tracks.
 * @property chapters A list of timed chapters for navigation.
 * @property timeRanges Specific ranges of interest (e.g., intro, credits).
 * @property customData Additional flexible data in JSON format.
 */
@Serializable
data class MediaMetadata(
  val title: String? = null,
  val subtitle: String? = null,
  val description: String? = null,
  val posterUrl: String? = null,
  val seasonNumber: Int? = null,
  val episodeNumber: Int? = null,
  val viewport: String? = null,
  val subtitles: List<SubtitleTrack>? = null,
  val chapters: List<Chapter>? = null,
  val timeRanges: List<TimeRange>? = null,
  val customData: JsonObject? = null,
)

/**
 * Configuration for Digital Rights Management (DRM).
 *
 * @property keySystem The DRM system identifier (e.g., Widevine, PlayReady).
 * @property licenseUrl The URL to fetch the decryption license.
 * @property certificateUrl The URL to fetch the device certificate, if required.
 * @property multisession Whether to enable DRM session sharing across requests.
 */
@Serializable
data class DrmConfig(
  val keySystem: String,
  val licenseUrl: String,
  val certificateUrl: String? = null,
  val multisession: Boolean? = null,
  // TODO Add the security level
)

/**
 * Representation of a subtitle or closed caption track.
 *
 * @property label The display name of the track.
 * @property kind The type of track (e.g., "subtitles", "captions").
 * @property language The BCP 47 language code.
 * @property url The URL to the subtitle file (e.g., VTT).
 */
@Serializable
data class SubtitleTrack(
  val label: String,
  val kind: String,
  val language: String,
  val url: String,
)

/**
 * A navigation marker within the media content.
 *
 * @property identifier Unique identifier for the chapter.
 * @property title The name of the chapter.
 * @property posterUrl An optional thumbnail for this specific chapter.
 * @property startTime The start position in milliseconds.
 * @property endTime The end position in milliseconds.
 */
@Serializable
data class Chapter(
  val identifier: String? = null,
  val title: String,
  val posterUrl: String? = null,
  val startTime: Long,
  val endTime: Long,
)

/**
 * Defines a specific period within the media duration, often used for UX markers.
 *
 * @property startTime The start position in milliseconds.
 * @property endTime The end position in milliseconds.
 * @property type The category of the range (e.g., "blocked", "intro", "credits").
 */
@Serializable
data class TimeRange(
  val startTime: Long,
  val endTime: Long,
  val type: String,
)
