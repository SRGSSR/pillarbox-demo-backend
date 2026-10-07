package ch.srgssr.pillarbox.backend.test

/**
 * Real Integration Layer `mediaComposition` responses used as test fixtures.
 */
object IntegrationLayerFixtures {
  /** A full-length VOD composition with chapters, a VTT subtitle track, and HD/SD stream variants. */
  val vodComposition: String get() = read("media-composition-vod.json")

  /** A live composition with DVR/non-DVR duplicates protected by FairPlay, Widevine, and PlayReady. */
  val liveDrmComposition: String get() = read("media-composition-live-drm.json")

  private fun read(name: String): String =
    checkNotNull(IntegrationLayerFixtures::class.java.getResource("/integrationlayer/$name")) {
      "Missing fixture: $name"
    }.readText()
}
