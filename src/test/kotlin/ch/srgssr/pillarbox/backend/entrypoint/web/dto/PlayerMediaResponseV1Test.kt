package ch.srgssr.pillarbox.backend.entrypoint.web.dto

import ch.srgssr.pillarbox.backend.test.MediaLibrary
import ch.srgssr.pillarbox.backend.test.mediaFixture
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class PlayerMediaResponseV1Test :
  ShouldSpec({

    should("select the specific source when a valid mimeType is provided") {
      val media =
        mediaFixture {
          withDash()
          withHls()
        }

      val response = media.toPlayerResponse(mimeType = "application/x-mpegURL", keySystem = null)

      response.source shouldBe MediaLibrary.Hls
    }

    should("not return any source if the requested mimeType is not found") {
      val media =
        mediaFixture {
          withDash()
          withHls()
        }

      val response = media.toPlayerResponse(mimeType = "video/mp4", keySystem = null)

      response.source shouldBe null
    }

    should("not return any source if the requested mimeType is null") {
      val media =
        mediaFixture {
          withDash()
          withHls()
        }

      val response = media.toPlayerResponse(mimeType = null, keySystem = null)

      response.source shouldBe null
    }

    should("select the correct DRM config based on keySystem") {
      val media =
        mediaFixture {
          withWidevine()
          withFairPlay()
        }

      val response = media.toPlayerResponse(mimeType = null, keySystem = "com.apple.fps")

      response.drm shouldBe MediaLibrary.FairPlay
    }

    should("return null for DRM if the requested keySystem doesn't exist") {
      val media =
        mediaFixture {
          withWidevine()
        }

      val response = media.toPlayerResponse(mimeType = null, keySystem = "com.microsoft.playready")

      response.drm shouldBe null
    }

    should("correctly map all metadata fields from domain to DTO") {
      val media =
        mediaFixture {
          id = "media-id"
          metadata =
            metadata.copy(
              title = "Title",
              description = "Description",
              episodeNumber = 1,
            )
        }

      val response = media.toPlayerResponse(null, null)

      response.identifier shouldBe "media-id"
      response.title shouldBe "Title"
      response.description shouldBe "Description"
      response.episodeNumber shouldBe 1
    }

    should("handle empty sources or drm lists gracefully") {
      val media = mediaFixture {}

      val emptyMedia = media.copy(sources = emptyList(), drmConfigs = emptyList())

      val response = emptyMedia.toPlayerResponse(null, null)

      response.source shouldBe null
      response.drm shouldBe null
    }
  })
