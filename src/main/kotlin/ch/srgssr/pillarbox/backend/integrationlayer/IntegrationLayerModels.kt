package ch.srgssr.pillarbox.backend.integrationlayer

import kotlinx.serialization.Serializable

/**
 * The Integration Layer media composition returned for a URN lookup.
 *
 * Only the fields consumed by the import mapping are declared; the lenient
 * JSON configuration ignores the rest of the payload.
 *
 * @property chapterUrn The URN of the requested (main) chapter.
 * @property episode The episode the media belongs to, if any.
 * @property show The show the media belongs to, if any.
 * @property chapterList The physical chapters carrying the playable resources.
 */
@Serializable
data class MediaComposition(
  val chapterUrn: String,
  val episode: Episode? = null,
  val show: Show? = null,
  val chapterList: List<CompositionChapter> = emptyList(),
) {
  /**
   * The chapter matching [chapterUrn], falling back to the first chapter.
   *
   * @return The main chapter, or null when the composition has no chapters.
   */
  val mainChapter: CompositionChapter?
    get() = chapterList.firstOrNull { it.urn == chapterUrn } ?: chapterList.firstOrNull()
}

/**
 * A physical media chapter with its descriptive fields and stream resources.
 *
 * @property urn The unique URN of the chapter.
 * @property title The display title of the chapter.
 * @property lead A short teaser text.
 * @property description A detailed description of the content.
 * @property imageUrl URL to an image representing the chapter.
 * @property mediaType The kind of media, either `VIDEO` or `AUDIO`.
 * @property subtitleList The external subtitle tracks available for the chapter.
 * @property resourceList The playable stream resources, best variants first.
 * @property segmentList The logical segments contained in the chapter.
 */
@Serializable
data class CompositionChapter(
  val urn: String,
  val title: String,
  val lead: String? = null,
  val description: String? = null,
  val imageUrl: String? = null,
  val mediaType: String? = null,
  val subtitleList: List<Subtitle> = emptyList(),
  val resourceList: List<Resource> = emptyList(),
  val segmentList: List<Segment> = emptyList(),
)

/**
 * A logical segment within a chapter, delimited by mark-in/mark-out positions.
 *
 * @property urn The unique URN of the segment.
 * @property title The display title of the segment.
 * @property imageUrl URL to an image representing the segment.
 * @property markIn The start position in milliseconds.
 * @property markOut The end position in milliseconds.
 */
@Serializable
data class Segment(
  val urn: String,
  val title: String,
  val imageUrl: String? = null,
  val markIn: Long? = null,
  val markOut: Long? = null,
)

/**
 * A playable stream resource of a chapter.
 *
 * @property url The URL of the media stream.
 * @property streaming The streaming method (e.g., `HLS`, `DASH`, `PROGRESSIVE`).
 * @property mimeType The MIME type of the content.
 * @property quality The stream quality (e.g., `SD`, `HD`).
 * @property dvr Whether the stream supports DVR (time-shift).
 * @property live Whether the stream is a live broadcast.
 * @property drmList The DRM systems protecting the stream, empty when clear.
 */
@Serializable
data class Resource(
  val url: String,
  val streaming: String? = null,
  val mimeType: String? = null,
  val quality: String? = null,
  val dvr: Boolean = false,
  val live: Boolean = false,
  val drmList: List<Drm> = emptyList(),
)

/**
 * A DRM system entry protecting a stream resource.
 *
 * @property type The DRM system (`FAIRPLAY`, `WIDEVINE`, or `PLAYREADY`).
 * @property licenseUrl The URL of the license server.
 * @property certificateUrl The URL of the certificate, used by FairPlay.
 */
@Serializable
data class Drm(
  val type: String,
  val licenseUrl: String,
  val certificateUrl: String? = null,
)

/**
 * An external subtitle track of a chapter.
 *
 * @property url The URL of the subtitle file.
 * @property format The file format (e.g., `VTT`, `TTML`).
 * @property locale The BCP 47 language code of the track.
 * @property language The human-readable language name.
 * @property type The track purpose (e.g., `SDH` for hard-of-hearing captions).
 */
@Serializable
data class Subtitle(
  val url: String,
  val format: String? = null,
  val locale: String? = null,
  val language: String? = null,
  val type: String? = null,
)

/**
 * The episode a media belongs to.
 *
 * @property seasonNumber The season index if part of a series.
 * @property number The episode index within the season.
 */
@Serializable
data class Episode(
  val seasonNumber: Int? = null,
  val number: Int? = null,
)

/**
 * The show a media belongs to.
 *
 * @property title The display title of the show.
 */
@Serializable
data class Show(
  val title: String? = null,
)
