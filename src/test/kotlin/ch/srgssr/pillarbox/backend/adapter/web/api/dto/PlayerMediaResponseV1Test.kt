package ch.srgssr.pillarbox.backend.adapter.web.api.dto

import ch.srgssr.pillarbox.backend.domain.model.DrmConfig
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.playback.MediaSourceSelector
import ch.srgssr.pillarbox.backend.domain.playback.toDrmPreferences
import ch.srgssr.pillarbox.backend.test.MediaLibrary
import ch.srgssr.pillarbox.backend.test.mediaFixture
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Instant

private data class SourceSelectionCase(
  val name: String,
  val media: Media,
  val mimeTypes: List<String>,
  val drm: List<String>,
  val expectedSource: PlayerMediaResponseV1.MediaSource?,
  val expectedDrm: DrmConfig?,
)

class PlayerMediaResponseV1Test :
  ShouldSpec({

    context("source and DRM selection") {
      withData(
        nameFn = { "should ${it.name}" },
        // MIME type selection
        SourceSelectionCase(
          name = "select the specific source when a valid mimeType is provided",
          media =
            mediaFixture {
              withDash()
              withHls()
            },
          mimeTypes = listOf("application/x-mpegURL"),
          drm = emptyList(),
          expectedSource = MediaLibrary.Hls.toPlayerMediaSourceV1(),
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "not return any source if the requested mimeType is not found",
          media =
            mediaFixture {
              withDash()
              withHls()
            },
          mimeTypes = listOf("video/mp4"),
          drm = emptyList(),
          expectedSource = null,
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "not return any source if the requested mimeType is null",
          media =
            mediaFixture {
              withDash()
              withHls()
            },
          mimeTypes = emptyList(),
          drm = emptyList(),
          expectedSource = null,
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "prefer the mimeType that appears earlier in the priority list",
          media =
            mediaFixture {
              withMp4()
              withHls()
              withDash()
            },
          mimeTypes = listOf("application/x-mpegURL", "application/dash+xml"),
          drm = emptyList(),
          expectedSource = MediaLibrary.Hls.toPlayerMediaSourceV1(),
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "match mimeType case-insensitively",
          media = mediaFixture { withHls() },
          mimeTypes = listOf("APPLICATION/X-MPEGURL"),
          drm = emptyList(),
          expectedSource = MediaLibrary.Hls.toPlayerMediaSourceV1(),
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "never select a source whose mimeType is null",
          media = mediaFixture { withSource(MediaLibrary.Dash.copy(mimeType = null)) },
          mimeTypes = listOf("application/dash+xml"),
          drm = emptyList(),
          expectedSource = null,
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "handle empty sources gracefully",
          media = mediaFixture {}.copy(sources = emptyList()),
          mimeTypes = listOf("application/dash+xml"),
          drm = emptyList(),
          expectedSource = null,
          expectedDrm = null,
        ),
        // DRM key system selection
        SourceSelectionCase(
          name = "select the correct DRM config based on keySystem",
          media = mediaFixture { withDash(MediaLibrary.Widevine, MediaLibrary.FairPlay) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.apple.fps"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.FairPlay,
        ),
        SourceSelectionCase(
          name = "return null for DRM if the requested keySystem doesn't exist",
          media = mediaFixture { withMp4(MediaLibrary.Widevine) },
          mimeTypes = listOf("video/mp4"),
          drm = listOf("com.microsoft.playready"),
          expectedSource = null,
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "prefer the keySystem that appears earlier in the priority list",
          media = mediaFixture { withDash(MediaLibrary.FairPlay, MediaLibrary.Widevine) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha", "com.apple.fps"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.Widevine,
        ),
        SourceSelectionCase(
          name = "strip non-matching DRM configs from the selected source",
          media = mediaFixture { withDash(MediaLibrary.Widevine, MediaLibrary.FairPlay) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.Widevine,
        ),
        SourceSelectionCase(
          name = "prefer a protected source over an unprotected one when a compatible keySystem is requested",
          media =
            mediaFixture {
              withDash()
              withHls(MediaLibrary.Widevine)
            },
          mimeTypes = listOf("application/dash+xml", "application/x-mpegURL"),
          drm = listOf("com.widevine.alpha"),
          expectedSource = MediaLibrary.Hls.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.Widevine,
        ),
        SourceSelectionCase(
          name = "return an unprotected source when an incompatible keySystem is requested",
          media =
            mediaFixture {
              withDash()
              withHls(MediaLibrary.Widevine)
            },
          mimeTypes = listOf("application/x-mpegURL", "application/dash+xml"),
          drm = listOf("com.microsoft.playready"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "exclude a protected source and fall back to unprotected when keySystems is empty",
          media =
            mediaFixture {
              withDash(MediaLibrary.Widevine)
              withHls()
            },
          mimeTypes = listOf("application/dash+xml", "application/x-mpegURL"),
          drm = emptyList(),
          expectedSource = MediaLibrary.Hls.toPlayerMediaSourceV1(),
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "prefer the source whose DRM key system appears earlier in the priority list when mimeTypes are equal",
          media =
            mediaFixture {
              withDash(MediaLibrary.FairPlay)
              withDash(MediaLibrary.Widevine)
            },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha", "com.apple.fps"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.Widevine,
        ),
        SourceSelectionCase(
          name = "return null source when all sources are protected and keySystems is empty",
          media =
            mediaFixture {
              withDash(MediaLibrary.Widevine)
              withHls(MediaLibrary.FairPlay)
            },
          mimeTypes = listOf("application/dash+xml", "application/x-mpegURL"),
          drm = emptyList(),
          expectedSource = null,
          expectedDrm = null,
        ),
        // Security level filtering (native levels)
        SourceSelectionCase(
          name = "include a DRM config with no security level regardless of the client security level",
          media = mediaFixture { withDash(MediaLibrary.Widevine) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha;L3"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.Widevine,
        ),
        SourceSelectionCase(
          name = "include a DRM config whose security level matches the client exactly",
          media = mediaFixture { withDash(MediaLibrary.WidevineL3) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha;L3"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.WidevineL3,
        ),
        SourceSelectionCase(
          name = "include a DRM config with a less restrictive security level than the client",
          media = mediaFixture { withDash(MediaLibrary.WidevineL3) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha;L1"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.WidevineL3,
        ),
        SourceSelectionCase(
          name = "exclude a DRM config whose security level is more restrictive than the client supports",
          media = mediaFixture { withDash(MediaLibrary.WidevineL1) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha;L3"),
          expectedSource = null,
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "prefer a DRM config compatible with the client security level over an incompatible one",
          media =
            mediaFixture {
              withDash(MediaLibrary.WidevineL1)
              withHls(MediaLibrary.WidevineL3)
            },
          mimeTypes = listOf("application/dash+xml", "application/x-mpegURL"),
          drm = listOf("com.widevine.alpha;L3"),
          expectedSource = MediaLibrary.Hls.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.WidevineL3,
        ),
        SourceSelectionCase(
          name = "exclude a stronger-required source when a known key system has no level (Widevine)",
          media = mediaFixture { withDash(MediaLibrary.WidevineL1) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha"),
          expectedSource = null,
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "match a weakest-level source when a known key system has no level (Widevine)",
          media = mediaFixture { withDash(MediaLibrary.WidevineL3) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.WidevineL3,
        ),
        SourceSelectionCase(
          name = "exclude a stronger-required source when a known key system has no level (PlayReady)",
          media = mediaFixture { withDash(MediaLibrary.PlayReadySL3000) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.microsoft.playready"),
          expectedSource = null,
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "match a weakest-level source when a known key system has no level (PlayReady)",
          media = mediaFixture { withDash(MediaLibrary.PlayReadySL2000) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.microsoft.playready"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.PlayReadySL2000,
        ),
        SourceSelectionCase(
          name = "exclude DRM config whose security level constraint is unknown for the key system",
          media = mediaFixture { withDash(MediaLibrary.WidevineL1) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha;SL3000"),
          expectedSource = null,
          expectedDrm = null,
        ),
        // Security level filtering (EME robustness levels)
        SourceSelectionCase(
          name = "resolve HW_SECURE_ALL to L1 for Widevine and match an L1-required source",
          media = mediaFixture { withDash(MediaLibrary.WidevineL1) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha;HW_SECURE_ALL"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.WidevineL1,
        ),
        SourceSelectionCase(
          name = "resolve HW_SECURE_CRYPTO to L2 for Widevine and exclude an L1-required source",
          media = mediaFixture { withDash(MediaLibrary.WidevineL1) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha;HW_SECURE_CRYPTO"),
          expectedSource = null,
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "resolve SW_SECURE_CRYPTO to L3 for Widevine and match an L3-required source",
          media = mediaFixture { withDash(MediaLibrary.WidevineL3) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha;SW_SECURE_CRYPTO"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.WidevineL3,
        ),
        SourceSelectionCase(
          name = "resolve SW_SECURE_DECODE to L3 for Widevine and exclude an L1-required source",
          media = mediaFixture { withDash(MediaLibrary.WidevineL1) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha;SW_SECURE_DECODE"),
          expectedSource = null,
          expectedDrm = null,
        ),
        SourceSelectionCase(
          name = "resolve HW_SECURE_DECODE to L2 for Widevine and match an L3-required source",
          media = mediaFixture { withDash(MediaLibrary.WidevineL3) },
          mimeTypes = listOf("application/dash+xml"),
          drm = listOf("com.widevine.alpha;HW_SECURE_DECODE"),
          expectedSource = MediaLibrary.Dash.toPlayerMediaSourceV1(),
          expectedDrm = MediaLibrary.WidevineL3,
        ),
      ) { case ->
        val selector = MediaSourceSelector(case.mimeTypes, case.drm.toDrmPreferences())
        val response = case.media.toPlayerResponse(selector)

        response.source shouldBe case.expectedSource
        response.drm shouldBe case.expectedDrm
      }
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

      val response = media.toPlayerResponse(MediaSourceSelector(emptyList(), emptyList()))

      response.identifier shouldBe "media-id"
      response.title shouldBe "Title"
      response.description shouldBe "Description"
      response.episodeNumber shouldBe 1
    }

    context("expiration in custom data") {
      val selector = MediaSourceSelector(emptyList(), emptyList())

      should("expose the expiration as epoch milliseconds") {
        val media = mediaFixture { expiresAt = Instant.parse("2026-06-06T12:32:00Z") }

        val response = media.toPlayerResponse(selector)

        response.customData shouldBe
          buildJsonObject {
            put("expiresAt", JsonPrimitive(1780749120000L))
          }
      }

      should("keep the custom data defined on the metadata") {
        val media =
          mediaFixture {
            expiresAt = Instant.parse("2026-06-06T12:32:00Z")
            metadata =
              metadata.copy(
                customData =
                  buildJsonObject {
                    put("analyticsId", JsonPrimitive("abc"))
                  },
              )
          }

        val response = media.toPlayerResponse(selector)

        response.customData shouldBe
          buildJsonObject {
            put("analyticsId", JsonPrimitive("abc"))
            put("expiresAt", JsonPrimitive(1780749120000L))
          }
      }

      should("omit the expiration when the media never expires") {
        val media =
          mediaFixture {
            metadata =
              metadata.copy(
                customData =
                  buildJsonObject {
                    put("analyticsId", JsonPrimitive("abc"))
                  },
              )
          }

        val response = media.toPlayerResponse(selector)

        response.customData shouldBe
          buildJsonObject {
            put("analyticsId", JsonPrimitive("abc"))
          }
      }
    }
  })
