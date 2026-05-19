package ch.srgssr.pillarbox.backend.entrypoint.web.api

import ch.srgssr.pillarbox.backend.entrypoint.web.dto.MediaResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.TagActionV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.TagBatchUpdateRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.TagOperationV1
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.toMediaRequestV1
import ch.srgssr.pillarbox.backend.test.token
import ch.srgssr.pillarbox.backend.test.tokenWithRoles
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class MediaRouteTest :
  ShouldSpec({
    should("return an empty list if no media is stored") {
      testApplicationContext {
        val response =
          client.get("/v1/media") {
            bearerAuth(token)
          }

        response shouldHaveStatus HttpStatusCode.OK

        println("DEBUG: The raw JSON received was: '${response.bodyAsText()}'")
        val mediaList = response.body<List<MediaResponseV1>>()

        mediaList.shouldBeEmpty()
      }
    }

    should("create a media, update the tags and delete it") {
      testApplicationContext {
        val fixture = mediaFixture { id = "test-media-id" }
        val request = fixture.toMediaRequestV1()

        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(request)
        } shouldHaveStatus HttpStatusCode.Created

        val tagUpdate =
          TagBatchUpdateRequestV1(
            operations =
              listOf(
                TagOperationV1(TagActionV1.ADD, listOf("test-tag")),
              ),
          )

        client.patch("/v1/media/${fixture.id}/tags") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(tagUpdate)
        } shouldHaveStatus HttpStatusCode.OK

        val response =
          client.get("/v1/media/${fixture.id}") {
            bearerAuth(token)
          }
        response.body<MediaResponseV1>().tags shouldContain "test-tag"

        client.delete("/v1/media/${fixture.id}") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NoContent
        client.get("/v1/media/${fixture.id}") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NotFound
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

        client
          .getMediaPageV1(limit = 5, offset = 0) {
            bearerAuth(token)
          }.let { page ->
            page.size shouldBe 5
            page.first().id shouldBe "media-0"
          }

        client
          .getMediaPageV1(limit = 1, offset = 5) {
            bearerAuth(token)
          }.let { page ->
            page.size shouldBe 1
            page.first().id shouldBe "media-5"
          }

        client
          .getMediaPageV1(limit = 1, offset = 20) {
            bearerAuth(token)
          }.size shouldBe 0
      }
    }

    should("return NOT_FOUND when patching tags of a non-existent media") {
      testApplicationContext {
        val tagUpdate =
          TagBatchUpdateRequestV1(
            operations =
              listOf(
                TagOperationV1(TagActionV1.ADD, listOf("some-tag")),
              ),
          )

        client.patch("/v1/media/does-not-exist/tags") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(tagUpdate)
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return NOT_FOUND when deleting a non-existent media") {
      testApplicationContext {
        client.delete("/v1/media/does-not-exist") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return NOT_FOUND when restoring a non-existent media") {
      testApplicationContext {
        client.post("/v1/media/does-not-exist/restore") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("update lastModified but preserve createdAt when media is modified") {
      testApplicationContext {
        val fixture = mediaFixture()
        val request = fixture.toMediaRequestV1()

        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(request)
        } shouldHaveStatus HttpStatusCode.Created

        val initialMedia =
          client
            .get("/v1/media/${fixture.id}") {
              bearerAuth(token)
            }.body<MediaResponseV1>()

        val firstCreatedAt = initialMedia.createdAt
        val firstLastModified = initialMedia.lastModified

        val tagUpdate =
          TagBatchUpdateRequestV1(
            operations =
              listOf(
                TagOperationV1(TagActionV1.ADD, listOf("timestamp-change-tag")),
              ),
          )

        client.patch("/v1/media/${fixture.id}/tags") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(tagUpdate)
        } shouldHaveStatus HttpStatusCode.OK

        val updatedMedia =
          client
            .get("/v1/media/${fixture.id}") {
              bearerAuth(token)
            }.body<MediaResponseV1>()

        updatedMedia.createdAt shouldBe firstCreatedAt
        (firstLastModified < updatedMedia.lastModified) shouldBe true
      }
    }
    should("return 401 on all endpoints when no token is provided") {
      testApplicationContext {
        client.get("/v1/media") shouldHaveStatus HttpStatusCode.Unauthorized
        client.get("/v1/media/any-id") shouldHaveStatus HttpStatusCode.Unauthorized
        client.post("/v1/media") { contentType(ContentType.Application.Json) } shouldHaveStatus
          HttpStatusCode.Unauthorized
        client.patch("/v1/media/any-id/tags") { contentType(ContentType.Application.Json) } shouldHaveStatus
          HttpStatusCode.Unauthorized
        client.delete("/v1/media/any-id") shouldHaveStatus HttpStatusCode.Unauthorized
        client.post("/v1/media/any-id/restore") shouldHaveStatus HttpStatusCode.Unauthorized
      }
    }

    should("allow read access but return 403 on write endpoints when authenticated with no roles") {
      testApplicationContext {
        val t = tokenWithRoles(emptySet())

        client.get("/v1/media") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.OK
        client.get("/v1/media/any-media") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.NotFound
        client.post("/v1/media") {
          bearerAuth(t)
          contentType(ContentType.Application.Json)
          setBody(mediaFixture().toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Forbidden
        client.patch("/v1/media/any-id/tags") {
          bearerAuth(t)
          contentType(ContentType.Application.Json)
        } shouldHaveStatus HttpStatusCode.Forbidden
        client.delete("/v1/media/any-id") { bearerAuth(t) } shouldHaveStatus HttpStatusCode.Forbidden
        client.post("/v1/media/any-id/restore") { bearerAuth(t) } shouldHaveStatus HttpStatusCode.Forbidden
      }
    }

    should("allow WRITE token to access all endpoints") {
      testApplicationContext {
        val fixture = mediaFixture { id = "auth-test-id" }

        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(fixture.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        client.get("/v1/media") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.OK
        client.get("/v1/media/${fixture.id}") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.OK
        client.delete("/v1/media/${fixture.id}") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.NoContent
        client.post("/v1/media/${fixture.id}/restore") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.Created
      }
    }

    should("successfully restore a deleted media item") {
      testApplicationContext {
        val fixture = mediaFixture()
        val request = fixture.toMediaRequestV1()

        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(request)
        } shouldHaveStatus HttpStatusCode.Created

        client.delete("/v1/media/${fixture.id}") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NoContent

        client.get("/v1/media/${fixture.id}") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NotFound

        client.post("/v1/media/${fixture.id}/restore") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.Created

        val restoredMedia =
          client
            .get("/v1/media/${fixture.id}") {
              bearerAuth(token)
            }.body<MediaResponseV1>()

        restoredMedia.id shouldBe fixture.id
      }
    }
  })

/**
 * Extension to fetch media with pagination parameters.
 */
suspend fun HttpClient.getMediaPageV1(
  limit: Int,
  offset: Int,
  block: HttpRequestBuilder.() -> Unit = {},
): List<MediaResponseV1> =
  get("/v1/media") {
    url {
      parameters.append("limit", limit.toString())
      parameters.append("offset", offset.toString())
    }

    block()
  }.body()
