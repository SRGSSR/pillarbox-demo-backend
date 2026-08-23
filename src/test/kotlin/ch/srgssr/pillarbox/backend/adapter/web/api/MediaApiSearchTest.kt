package ch.srgssr.pillarbox.backend.adapter.web.api

import ch.srgssr.pillarbox.backend.adapter.web.api.dto.MediaResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.PlayerMediaResponseV1
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.MediaMetadata
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.toMediaRequestV1
import ch.srgssr.pillarbox.backend.test.token
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder

class MediaApiSearchTest :
  ShouldSpec({

    fun media(title: String) = mediaFixture { metadata = MediaMetadata(title = title) }

    suspend fun ApplicationTestBuilder.create(media: Media) =
      client.post("/v1/media") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(media.toMediaRequestV1())
      } shouldHaveStatus HttpStatusCode.Created

    should("filter the management media list by the q parameter") {
      testApplicationContext {
        val alps = media("Sunrise over the Alps")
        create(alps)
        create(media("City nightlife"))

        val results =
          client
            .get("/v1/media?q=alps") { bearerAuth(token) }
            .body<List<MediaResponseV1>>()

        results.map { it.id } shouldBe listOf(alps.id)
      }
    }

    should("filter the public player media list by the q parameter") {
      testApplicationContext {
        val alps = media("Sunrise over the Alps")
        create(alps)
        create(media("City nightlife"))

        val results =
          client
            .get("/v1/player/media?q=alps")
            .body<List<PlayerMediaResponseV1>>()

        results.map { it.identifier } shouldBe listOf(alps.id)
      }
    }

    should("exclude soft-deleted media from public player search") {
      testApplicationContext {
        val gone = media("Glacier descent")
        create(gone)
        client.delete("/v1/media/${gone.id}") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.NoContent

        val results =
          client
            .get("/v1/player/media?q=glacier")
            .body<List<PlayerMediaResponseV1>>()

        results.map { it.identifier } shouldBe emptyList()
      }
    }
  })
