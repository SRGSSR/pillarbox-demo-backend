package ch.srgssr.pillarbox.backend.entrypoint.web

import ch.srgssr.pillarbox.backend.entrypoint.web.dto.MediaResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.TagActionV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.TagBatchUpdateRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.TagOperationV1
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.toMediaRequestV1
import ch.srgssr.pillarbox.backend.test.token
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

        // Create the media
        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(request)
        } shouldHaveStatus HttpStatusCode.Created

        // Update tags
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

        // Verify tags
        val response =
          client.get("/v1/media/${fixture.id}") {
            bearerAuth(token)
          }
        response.body<MediaResponseV1>().tags shouldContain "test-tag"

        // Delete the Media
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
