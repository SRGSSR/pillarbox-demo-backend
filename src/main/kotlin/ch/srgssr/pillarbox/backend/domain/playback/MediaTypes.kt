package ch.srgssr.pillarbox.backend.domain.playback

/**
 * Well-known MIME types. The API accepts arbitrary strings; these are just the
 * values we reference ourselves.
 * See https://www.iana.org/assignments/media-types/media-types.xhtml
 */
object MimeTypes {
  // Audio
  const val MP3 = "audio/mpeg"
  const val AAC = "audio/aac"
  const val M4A = "audio/mp4"
  const val FLAC = "audio/flac"
  const val WAV = "audio/wav"
  const val OGG = "audio/ogg"
  const val OPUS = "audio/opus"
  const val WEBM_AUDIO = "audio/webm"

  // Video
  const val MP4 = "video/mp4"
  const val WEBM = "video/webm"
  const val MKV = "video/x-matroska"
  const val MOV = "video/quicktime"
  const val TS = "video/mp2t"
  const val THREE_GPP = "video/3gpp"

  // Adaptive streaming manifests
  const val HLS = "application/vnd.apple.mpegurl"
  const val HLS_LEGACY = "application/x-mpegURL"
  const val DASH = "application/dash+xml"
  const val SMOOTH_STREAMING = "application/vnd.ms-sstr+xml"
}

/** EME key system identifiers. See https://www.w3.org/TR/encrypted-media/ */
object DrmSystems {
  const val WIDEVINE = "com.widevine.alpha"
  const val FAIRPLAY = "com.apple.fps"
  const val PLAYREADY = "com.microsoft.playready"
}
