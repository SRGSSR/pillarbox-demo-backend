package ch.srgssr.pillarbox.backend.test

/**
 * Trimmed real Integration Layer `mediaComposition` responses used as test fixtures.
 */
object IntegrationLayerFixtures {
  /** A VOD composition with segments, VTT/TTML subtitles, and HD/SD stream variants. */
  val vodComposition: String get() = read("media-composition-vod.json")

  /** A live composition with DVR/non-DVR duplicates protected by FairPlay, Widevine, and PlayReady. */
  val liveDrmComposition: String get() = read("media-composition-live-drm.json")

  private fun read(name: String): String =
    checkNotNull(IntegrationLayerFixtures::class.java.getResource("/integrationlayer/$name")) {
      "Missing fixture: $name"
    }.readText()
}
