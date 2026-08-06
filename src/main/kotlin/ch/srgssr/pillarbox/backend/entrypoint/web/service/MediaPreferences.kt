package ch.srgssr.pillarbox.backend.entrypoint.web.service

import ch.srgssr.pillarbox.backend.entrypoint.web.utils.DrmSystems
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.MimeTypes

/**
 * A client's combined media capabilities, pairing prioritised MIME types
 * with prioritised DRM preferences.
 *
 * Ready-made presets for common platforms are available in the companion object.
 *
 * @property mimeTypePreferences Prioritised list of accepted MIME types.
 * @property drmPreferences Prioritised list of accepted DRM preferences.
 */
data class MediaPreferences(
  val mimeTypePreferences: List<String> = listOf(),
  val drmPreferences: List<DrmPreference> = listOf(),
) {
  companion object {
    /** Preset for Android clients: DASH-first with Widevine, MP3 priority for aod. */
    val android =
      MediaPreferences(
        mimeTypePreferences =
          listOf(
            MimeTypes.MP3,
            MimeTypes.DASH,
            MimeTypes.HLS,
            MimeTypes.HLS_LEGACY,
            MimeTypes.SMOOTH_STREAMING,
            MimeTypes.AAC,
            MimeTypes.M4A,
            MimeTypes.MP4,
            MimeTypes.WEBM,
            MimeTypes.WEBM_AUDIO,
            MimeTypes.MKV,
            MimeTypes.FLAC,
            MimeTypes.OGG,
            MimeTypes.OPUS,
            MimeTypes.WAV,
            MimeTypes.TS,
            MimeTypes.THREE_GPP,
          ),
        drmPreferences =
          listOf(
            DrmPreference(DrmSystems.WIDEVINE),
          ),
      )

    /** Preset for Apple clients: HLS-first with FairPlay, MP3 priority for aod. */
    val apple =
      MediaPreferences(
        mimeTypePreferences =
          listOf(
            MimeTypes.MP3,
            MimeTypes.HLS,
            MimeTypes.HLS_LEGACY,
            MimeTypes.AAC,
            MimeTypes.M4A,
            MimeTypes.MP4,
            MimeTypes.MOV,
            MimeTypes.FLAC,
            MimeTypes.WAV,
            MimeTypes.THREE_GPP,
          ),
        drmPreferences =
          listOf(
            DrmPreference(DrmSystems.FAIRPLAY),
          ),
      )

    /** Preset for web browser clients: HLS/DASH-first, no DRM assumption, MP3 first for aod. */
    val web =
      MediaPreferences(
        mimeTypePreferences =
          listOf(
            MimeTypes.MP3,
            MimeTypes.HLS,
            MimeTypes.HLS_LEGACY,
            MimeTypes.DASH,
            MimeTypes.SMOOTH_STREAMING,
            MimeTypes.AAC,
            MimeTypes.M4A,
            MimeTypes.MP4,
            MimeTypes.WEBM,
            MimeTypes.WEBM_AUDIO,
            MimeTypes.OGG,
            MimeTypes.OPUS,
            MimeTypes.FLAC,
            MimeTypes.WAV,
          ),
        drmPreferences = emptyList(),
      )

    /** The ready-made preset of each platform, keyed by its lowercase identifier. */
    val platformPresets: Map<String, MediaPreferences> =
      mapOf(
        "android" to android,
        "apple" to apple,
        "web" to web,
      )

    /** The platform identifiers a client may ask for, in lowercase. */
    val knownPlatforms: Set<String> = platformPresets.keys
  }
}

/**
 * Resolves a raw platform identifier to its ready-made [MediaPreferences] preset.
 *
 * Recognised identifiers (case-insensitive): `android`, `apple`, `web`.
 *
 * @receiver the raw platform identifier.
 * @return the preset of that platform, or `null` if the identifier is unrecognised.
 */
fun String.toPlatformPreferences(): MediaPreferences? = MediaPreferences.platformPresets[lowercase()]
