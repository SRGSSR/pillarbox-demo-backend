package ch.srgssr.pillarbox.backend.test

import ch.srgssr.pillarbox.backend.domain.model.Chapter
import ch.srgssr.pillarbox.backend.domain.model.DrmConfig
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.MediaMetadata
import ch.srgssr.pillarbox.backend.domain.model.MediaSource
import ch.srgssr.pillarbox.backend.domain.model.SubtitleTrack
import ch.srgssr.pillarbox.backend.domain.model.TimeRange
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.MediaRequestV1

object MediaLibrary {
  // Sources
  val Dash = MediaSource(url = "https://stream.com/dash/index.mpd", mimeType = "application/dash+xml")
  val Hls = MediaSource(url = "https://stream.com/hls/master.m3u8", mimeType = "application/x-mpegURL")
  val Mp4 = MediaSource(url = "https://stream.com/video.mp4", mimeType = "video/mp4")

  // DRM Configs
  val Widevine = DrmConfig(keySystem = "com.widevine.alpha", licenseUrl = "https://wv.license.com")
  val PlayReady = DrmConfig(keySystem = "com.microsoft.playready", licenseUrl = "https://pr.license.com")
  val FairPlay = DrmConfig(keySystem = "com.apple.fps", licenseUrl = "https://fp.license.com")
  val ClearKey = DrmConfig(keySystem = "org.w3.clearkey", licenseUrl = "https://ck.license.com")

  // Metadata
  val EnglishSubtitles =
    SubtitleTrack(
      label = "English",
      kind = "subtitles",
      language = "en",
      url = "https://stream.com/en.vtt",
    )

  val IntroRange =
    TimeRange(
      startTime = 0L,
      endTime = 30000L,
      type = "intro",
    )

  val FirstChapter =
    Chapter(
      title = "Opening",
      startTime = 0L,
      endTime = 60000L,
    )
}

class MediaBuilder {
  var id: String = "test-media-id"
  private val sources = mutableListOf<MediaSource>()
  private val drmConfigs = mutableListOf<DrmConfig>()
  private val subtitles = mutableListOf<SubtitleTrack>()
  private val chapters = mutableListOf<Chapter>()
  private val timeRanges = mutableListOf<TimeRange>()
  var metadata = MediaMetadata(title = "Default Test Title")

  fun withDash() = apply { sources.add(MediaLibrary.Dash) }

  fun withHls() = apply { sources.add(MediaLibrary.Hls) }

  fun withMp4() = apply { sources.add(MediaLibrary.Mp4) }

  fun withWidevine() = apply { drmConfigs.add(MediaLibrary.Widevine) }

  fun withPlayReady() = apply { drmConfigs.add(MediaLibrary.PlayReady) }

  fun withFairPlay() = apply { drmConfigs.add(MediaLibrary.FairPlay) }

  fun withClearKey() = apply { drmConfigs.add(MediaLibrary.ClearKey) }

  fun withSubtitles() = apply { subtitles.add(MediaLibrary.EnglishSubtitles) }

  fun withIntro() = apply { timeRanges.add(MediaLibrary.IntroRange) }

  fun withChapters() = apply { chapters.add(MediaLibrary.FirstChapter) }

  fun build() =
    Media(
      id = id,
      sources = sources.ifEmpty { listOf(MediaLibrary.Dash) }, // Sane default
      drmConfigs = drmConfigs,
      metadata =
        metadata.copy(
          subtitles = subtitles.ifEmpty { null },
          chapters = chapters.ifEmpty { null },
          timeRanges = timeRanges.ifEmpty { null },
        ),
    )
}

fun mediaFixture(block: MediaBuilder.() -> Unit = {}) = MediaBuilder().apply(block).build()

fun Media.toMediaRequestV1() =
  MediaRequestV1(
    id = id,
    tags = tags,
    sources = sources,
    drmConfigs = drmConfigs,
    metadata = metadata,
  )
