package ch.srgssr.pillarbox.backend.entrypoint.web.api

import ch.srgssr.pillarbox.backend.entrypoint.web.dto.MediaResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.PlayerMediaResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toPlayerMediaSourceV1
import ch.srgssr.pillarbox.backend.test.MediaLibrary
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.shouldMatchSchema
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.toMediaRequestV1
import ch.srgssr.pillarbox.backend.test.token
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class PlayerMediaRouteTest :
  ShouldSpec({
    should("return an empty list if no media is stored") {
      testApplicationContext {
        val response = client.get("/v1/player/media")

        response shouldHaveStatus HttpStatusCode.OK

        val mediaList = response.body<List<MediaResponseV1>>()

        mediaList.shouldBeEmpty()
      }
    }

    should("serve a media matching the standard player specification") {
      testApplicationContext {
        val media =
          mediaFixture {
            withDash(MediaLibrary.Widevine)
            withHls()
            withSubtitles()
            withIntro()
            withChapters()
          }

        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        val response =
          client.get("/v1/player/media/${media.id}") {
            header("X-Accept-Stream-Type", "application/x-mpegURL")
            header("X-Accept-DRM", "com.widevine.alpha")
          }

        response shouldMatchSchema "schemas/pillarbox-standard-metadata-schema.json"
      }
    }

    should("return NOT_FOUND when retrieving a non-existent media") {
      testApplicationContext {
        client.get("/v1/player/media/does-not-exist") shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return paginated media correctly") {
      testApplicationContext {
        val totalMedia = 19
        for (i in 0..totalMedia) {
          val fixture = mediaFixture { id = "media-$i" }
          client.post("/v1/media") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(fixture.toMediaRequestV1())
          } shouldHaveStatus HttpStatusCode.Created
        }

        client.getPlayerMediaPageV1(limit = 5, offset = 0).let { page ->
          page.size shouldBe 5
          page.first().identifier shouldBe "media-0"
        }

        client.getPlayerMediaPageV1(limit = 1, offset = 5).let { page ->
          page.size shouldBe 1
          page.first().identifier shouldBe "media-5"
        }

        client.getPlayerMediaPageV1(limit = 1, offset = 20).size shouldBe 0
      }
    }

    should("select source and DRM via query parameters") {
      testApplicationContext {
        val media =
          mediaFixture {
            withMp4()
            withDash(MediaLibrary.WidevineL3)
            withHls()
          }
        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        val csvResponse =
          client
            .get("/v1/player/media/${media.id}") {
              url {
                parameters.append("stream-type", "application/x-mpegURL,video/mp4")
                parameters.append("drm", "com.widevine.alpha,com.apple.fps")
                parameters.append("security-level", "L3")
              }
            }.body<PlayerMediaResponseV1>()

        csvResponse.source shouldBe MediaLibrary.Hls.toPlayerMediaSourceV1()
        csvResponse.drm shouldBe null

        val multiResponse =
          client
            .get("/v1/player/media/${media.id}") {
              url {
                parameters.append("stream-type", "application/dash+xml")
                parameters.append("stream-type", "application/x-mpegURL")
                parameters.append("drm", "com.widevine.alpha")
                parameters.append("drm", "com.apple.fps")
                parameters.append("security-level", "L3")
              }
            }.body<PlayerMediaResponseV1>()
        multiResponse.source shouldBe MediaLibrary.Dash.toPlayerMediaSourceV1()
        multiResponse.drm shouldBe MediaLibrary.WidevineL3
      }
    }

    should("fall back to headers when query parameters are absent") {
      testApplicationContext {
        val media =
          mediaFixture {
            withDash(MediaLibrary.WidevineL3)
            withHls(MediaLibrary.FairPlay)
          }
        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        // Comma-separated headers
        val csvResponse =
          client
            .get("/v1/player/media/${media.id}") {
              header("X-Accept-Stream-Type", "application/dash+xml,application/x-mpegURL")
              header("X-Accept-DRM", "com.widevine.alpha;L3,com.apple.fps")
            }.body<PlayerMediaResponseV1>()
        csvResponse.source shouldBe MediaLibrary.Dash.toPlayerMediaSourceV1()
        csvResponse.drm shouldBe MediaLibrary.WidevineL3

        // Repeated headers
        val multiResponse =
          client
            .get("/v1/player/media/${media.id}") {
              header("X-Accept-Stream-Type", "application/x-mpegURL")
              header("X-Accept-Stream-Type", "application/dash+xml")
              header("X-Accept-DRM", "com.apple.fps")
            }.body<PlayerMediaResponseV1>()
        multiResponse.source shouldBe MediaLibrary.Hls.toPlayerMediaSourceV1()
        multiResponse.drm shouldBe MediaLibrary.FairPlay
      }
    }

    should("prefer query parameters over headers for stream-type, drm, and security-level") {
      testApplicationContext {
        val media =
          mediaFixture {
            withDash(MediaLibrary.WidevineL1)
            withHls(MediaLibrary.WidevineL3)
          }
        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        val response =
          client
            .get("/v1/player/media/${media.id}") {
              url {
                parameters.append("stream-type", "application/dash+xml")
                parameters.append("drm", "com.widevine.alpha")
                parameters.append("security-level", "L1")
              }
              header("X-Accept-Stream-Type", "application/x-mpegURL")
              header("X-Accept-DRM", "com.widevine.alpha;L3")
            }.body<PlayerMediaResponseV1>()

        response.source shouldBe MediaLibrary.Dash.toPlayerMediaSourceV1()
        response.drm shouldBe MediaLibrary.WidevineL1
      }
    }
  })

/**
 * Extension to fetch media with pagination parameters.
 */
suspend fun HttpClient.getPlayerMediaPageV1(
  limit: Int,
  offset: Int,
): List<PlayerMediaResponseV1> =
  get("/v1/player/media") {
    url {
      parameters.append("limit", limit.toString())
      parameters.append("offset", offset.toString())
    }
  }.body()
