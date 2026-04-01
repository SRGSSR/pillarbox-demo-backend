package ch.srgssr.pillarbox.backend.entrypoint.web.dto

import ch.srgssr.pillarbox.backend.test.MediaLibrary
import ch.srgssr.pillarbox.backend.test.mediaFixture
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PlayerMediaResponseV1Test :
  ShouldSpec({

    should("select the specific source when a valid mimeType is provided") {
      val media =
        mediaFixture {
          withDash()
          withHls()
        }

      val response = media.toPlayerResponse(mimeTypes = listOf("application/x-mpegURL"))

      response.source shouldBe MediaLibrary.Hls.toPlayerMediaSourceV1()
    }

    should("not return any source if the requested mimeType is not found") {
      val media =
        mediaFixture {
          withDash()
          withHls()
        }

      val response = media.toPlayerResponse(mimeTypes = listOf("video/mp4"))

      response.source shouldBe null
    }

    should("not return any source if the requested mimeType is null") {
      val media =
        mediaFixture {
          withDash()
          withHls()
        }

      val response = media.toPlayerResponse(mimeTypes = emptyList())

      response.source shouldBe null
    }

    should("prefer the mimeType that appears earlier in the priority list") {
      val media =
        mediaFixture {
          withMp4()
          withHls()
          withDash()
        }
      val response =
        media.toPlayerResponse(
          mimeTypes = listOf("application/x-mpegURL", "application/dash+xml"),
        )
      response.source shouldBe MediaLibrary.Hls.toPlayerMediaSourceV1()
    }

    should("select the correct DRM config based on keySystem") {
      val media =
        mediaFixture {
          withDash(MediaLibrary.Widevine, MediaLibrary.FairPlay)
        }

      val response =
        media.toPlayerResponse(
          mimeTypes = listOf("application/dash+xml"),
          keySystems = listOf("com.apple.fps"),
        )

      response.drm shouldBe MediaLibrary.FairPlay
    }

    should("return null for DRM if the requested keySystem doesn't exist") {
      val media =
        mediaFixture {
          withMp4(MediaLibrary.Widevine)
        }

      val response =
        media.toPlayerResponse(
          mimeTypes = listOf("video/mp4"),
          keySystems = listOf("com.microsoft.playready"),
        )

      response.drm shouldBe null
    }

    should("prefer the keySystem that appears earlier in the priority list") {
      val media =
        mediaFixture {
          withDash(MediaLibrary.FairPlay, MediaLibrary.Widevine)
        }
      val response =
        media.toPlayerResponse(
          mimeTypes = listOf("application/dash+xml"),
          keySystems = listOf("com.widevine.alpha", "com.apple.fps"),
        )
      response.drm shouldBe MediaLibrary.Widevine
    }

    should("strip non-matching DRM configs from the selected source's drm field") {
      val media =
        mediaFixture {
          withDash(MediaLibrary.Widevine, MediaLibrary.FairPlay)
        }
      val response =
        media.toPlayerResponse(
          mimeTypes = listOf("application/dash+xml"),
          keySystems = listOf("com.widevine.alpha"),
        )
      response.drm shouldBe MediaLibrary.Widevine
    }

    should("prefer a protected source over an unprotected one when a compatible keySystem is requested") {
      val media =
        mediaFixture {
          withDash()
          withHls(MediaLibrary.Widevine)
        }

      val response =
        media.toPlayerResponse(
          mimeTypes = listOf("application/dash+xml", "application/x-mpegURL"),
          keySystems = listOf("com.widevine.alpha"),
        )

      response.source shouldBe MediaLibrary.Hls.toPlayerMediaSourceV1()
      response.drm shouldBe MediaLibrary.Widevine
    }

    should("return an unprotected source when an incompatible keySystem is requested") {
      val media =
        mediaFixture {
          withDash()
          withHls(MediaLibrary.Widevine)
        }

      val response =
        media.toPlayerResponse(
          mimeTypes = listOf("application/x-mpegURL", "application/dash+xml"),
          keySystems = listOf("com.microsoft.playready"),
        )

      response.source shouldBe MediaLibrary.Dash.toPlayerMediaSourceV1()
    }

    should("exclude a protected source and fall back to unprotected when keySystems is empty") {
      val media =
        mediaFixture {
          withDash(MediaLibrary.Widevine)
          withHls()
        }
      val response =
        media.toPlayerResponse(
          mimeTypes = listOf("application/dash+xml", "application/x-mpegURL"),
          keySystems = emptyList(),
        )
      response.source shouldBe MediaLibrary.Hls.toPlayerMediaSourceV1()
      response.drm shouldBe null
    }

    should("prefer the source whose DRM key system appears earlier in the priority list when mimeTypes are equal") {
      val media =
        mediaFixture {
          withDash(MediaLibrary.FairPlay)
          withDash(MediaLibrary.Widevine)
        }
      val response =
        media.toPlayerResponse(
          mimeTypes = listOf("application/dash+xml"),
          keySystems = listOf("com.widevine.alpha", "com.apple.fps"),
        )
      response.drm shouldBe MediaLibrary.Widevine
    }

    should("return null source when all sources are protected and keySystems is empty") {
      val media =
        mediaFixture {
          withDash(MediaLibrary.Widevine)
          withHls(MediaLibrary.FairPlay)
        }
      val response =
        media.toPlayerResponse(
          mimeTypes = listOf("application/dash+xml", "application/x-mpegURL"),
          keySystems = emptyList(),
        )
      response.source shouldBe null
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

      val response = media.toPlayerResponse()

      response.identifier shouldBe "media-id"
      response.title shouldBe "Title"
      response.description shouldBe "Description"
      response.episodeNumber shouldBe 1
    }

    should("handle empty sources or drm lists gracefully") {
      val media = mediaFixture {}

      val emptyMedia = media.copy(sources = emptyList())

      val response = emptyMedia.toPlayerResponse(mimeTypes = listOf("application/dash+xml"))

      response.source shouldBe null
      response.drm shouldBe null
    }

    should("match mimeType case-insensitively") {
      val media =
        mediaFixture {
          withHls()
        }
      val response =
        media.toPlayerResponse(mimeTypes = listOf("APPLICATION/X-MPEGURL"))
      response.source shouldBe MediaLibrary.Hls.toPlayerMediaSourceV1()
    }

    should("never select a source whose mimeType is null") {
      val nullMimeTypeSource = MediaLibrary.Dash.copy(mimeType = null)
      val media =
        mediaFixture {
          withSource(nullMimeTypeSource)
        }

      val response =
        media.toPlayerResponse(mimeTypes = listOf("application/dash+xml"))
      response.source shouldBe null
    }
  })
