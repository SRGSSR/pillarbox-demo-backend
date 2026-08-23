package ch.srgssr.pillarbox.backend.adapter.web.api

import ch.srgssr.pillarbox.backend.adapter.web.api.dto.AssignMediaRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.MediaResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.PlayerMediaResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.toPlayerMediaSourceV1
import ch.srgssr.pillarbox.backend.test.MediaLibrary
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.shouldMatchSchema
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.toMediaRequestV1
import ch.srgssr.pillarbox.backend.test.token
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

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

    should("hide expired media from the player API") {
      testApplicationContext {
        val expired =
          mediaFixture {
            id = "expired"
            expiresAt = Clock.System.now() - 1.hours
          }
        val live =
          mediaFixture {
            id = "live"
            expiresAt = Clock.System.now() + 1.hours
          }

        for (media in listOf(expired, live)) {
          client.post("/v1/media") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(media.toMediaRequestV1())
          } shouldHaveStatus HttpStatusCode.Created
        }

        client.get("/v1/player/media/${expired.id}") shouldHaveStatus HttpStatusCode.NotFound
        client.get("/v1/player/media/${live.id}") shouldHaveStatus HttpStatusCode.OK

        client
          .getPlayerMediaPageV1(limit = 10, offset = 0)
          .map { it.identifier } shouldContainExactly listOf(live.id)
      }
    }

    should("hide expired media from a player folder listing") {
      testApplicationContext {
        val expired =
          mediaFixture {
            id = "expired"
            expiresAt = Clock.System.now() - 1.hours
          }
        val live = mediaFixture { id = "live" }

        val folder =
          client
            .post("/v1/folder") {
              bearerAuth(token)
              contentType(ContentType.Application.Json)
              setBody(FolderRequestV1(name = "Season 1"))
            }.body<FolderResponseV1>()

        for (media in listOf(expired, live)) {
          client.post("/v1/media") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(media.toMediaRequestV1())
          } shouldHaveStatus HttpStatusCode.Created

          client.post("/v1/folder/${folder.id}/media") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(AssignMediaRequestV1(mediaId = media.id))
          } shouldHaveStatus HttpStatusCode.Created
        }

        client
          .get("/v1/player/folder/${folder.id}/media")
          .body<List<PlayerMediaResponseV1>>()
          .map { it.identifier } shouldContainExactly listOf(live.id)
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
                parameters.append("drm", "com.widevine.alpha;L3,com.apple.fps")
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
                parameters.append("drm", "com.widevine.alpha;L3")
                parameters.append("drm", "com.apple.fps")
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

    should("prefer query parameters over headers for stream-type and drm") {
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
                parameters.append("drm", "com.widevine.alpha;L1")
              }
              header("X-Accept-Stream-Type", "application/x-mpegURL")
              header("X-Accept-DRM", "com.widevine.alpha;L3")
            }.body<PlayerMediaResponseV1>()

        response.source shouldBe MediaLibrary.Dash.toPlayerMediaSourceV1()
        response.drm shouldBe MediaLibrary.WidevineL1
      }
    }

    should("select an unprotected source by preset priority for the web platform") {
      testApplicationContext {
        val media =
          mediaFixture {
            withMp4()
            withDash()
            withHls()
          }
        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        val response =
          client
            .get("/v1/player/media/${media.id}") {
              url { parameters.append("platform", "web") }
            }.body<PlayerMediaResponseV1>()

        response.source shouldBe MediaLibrary.Hls.toPlayerMediaSourceV1()
        response.drm shouldBe null
      }
    }

    should("override the android preset DRM with the drm query parameter") {
      testApplicationContext {
        val media =
          mediaFixture {
            withDash(MediaLibrary.FairPlay)
            withHls()
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
                parameters.append("platform", "android")
                parameters.append("drm", "com.apple.fps")
              }
            }.body<PlayerMediaResponseV1>()

        response.source shouldBe MediaLibrary.Dash.toPlayerMediaSourceV1()
        response.drm shouldBe MediaLibrary.FairPlay
      }
    }

    should("override the apple preset stream types with the stream-type query parameter") {
      testApplicationContext {
        val media =
          mediaFixture {
            withDash(MediaLibrary.FairPlay)
            withHls()
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
                parameters.append("platform", "apple")
                parameters.append("stream-type", "application/dash+xml")
              }
            }.body<PlayerMediaResponseV1>()

        response.source shouldBe MediaLibrary.Dash.toPlayerMediaSourceV1()
        response.drm shouldBe MediaLibrary.FairPlay
      }
    }

    should("fall back to the X-Target-Platform header when the platform parameter is absent") {
      testApplicationContext {
        val media =
          mediaFixture {
            withMp4()
            withDash()
            withHls()
          }
        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        val response =
          client
            .get("/v1/player/media/${media.id}") {
              header("X-Target-Platform", "android")
            }.body<PlayerMediaResponseV1>()

        response.source shouldBe MediaLibrary.Dash.toPlayerMediaSourceV1()
        response.drm shouldBe null
      }
    }

    should("return BAD_REQUEST when the target platform is unknown") {
      testApplicationContext {
        val media = mediaFixture { withHls() }
        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        client.get("/v1/player/media/${media.id}") {
          url { parameters.append("platform", "playstation") }
        } shouldHaveStatus HttpStatusCode.BadRequest

        client.get("/v1/player/media/${media.id}") {
          url { parameters.append("platform", "web,android") }
        } shouldHaveStatus HttpStatusCode.BadRequest

        client.get("/v1/player/media") {
          header("X-Target-Platform", "playstation")
        } shouldHaveStatus HttpStatusCode.BadRequest
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
