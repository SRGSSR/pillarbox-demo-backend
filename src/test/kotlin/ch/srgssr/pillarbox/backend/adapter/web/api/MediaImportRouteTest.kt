package ch.srgssr.pillarbox.backend.adapter.web.api

import ch.srgssr.pillarbox.backend.adapter.web.api.dto.AssignMediaRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderPermissionRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.ImportMediaRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.MediaResponseV1
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.test.IntegrationLayerFixtures
import ch.srgssr.pillarbox.backend.test.seedUser
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.token
import ch.srgssr.pillarbox.backend.test.tokenFor
import ch.srgssr.pillarbox.backend.test.tokenWithRoles
import ch.srgssr.pillarbox.backend.test.userFixture
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking

/** The URN of the main chapter in [IntegrationLayerFixtures.vodComposition]. */
private const val VOD_URN = "urn:rsi:video:3845234"

/**
 * Runs a test application against a local stub of the Integration Layer that
 * answers every URN lookup with the given status and body.
 *
 * @param status The HTTP status the stub answers with.
 * @param contentType The content type the stub answers with.
 * @param body The response body the stub answers with.
 * @param block The test logic to execute against the application.
 */
private fun withStubbedIntegrationLayer(
  status: HttpStatusCode = HttpStatusCode.OK,
  contentType: ContentType = ContentType.Application.Json,
  body: String = IntegrationLayerFixtures.vodComposition,
  block: suspend ApplicationTestBuilder.() -> Unit,
) {
  val stub =
    embeddedServer(Netty, port = 0) {
      routing {
        get("/integrationlayer/2.0/mediaComposition/byUrn/{urn}") {
          call.respondText(body, contentType, status)
        }
      }
    }.start()

  try {
    val port =
      runBlocking {
        stub.engine
          .resolvedConnectors()
          .first()
          .port
      }
    testApplicationContext(configOverrides = mapOf("integrationLayer.baseUrl" to "http://localhost:$port")) {
      block()
    }
  } finally {
    stub.stop()
  }
}

/**
 * Imports [urn] through the Management API with the given bearer token.
 *
 * @param urn The URN to import.
 * @param bearer The bearer token used to authenticate the call.
 * @return The raw HTTP response.
 */
private suspend fun ApplicationTestBuilder.importV1(
  urn: String,
  bearer: String,
): HttpResponse =
  client.post("/v1/media-import") {
    bearerAuth(bearer)
    contentType(ContentType.Application.Json)
    setBody(ImportMediaRequestV1(urn = urn))
  }

class MediaImportRouteTest :
  ShouldSpec({

    should("import a media from the Integration Layer and persist it") {
      withStubbedIntegrationLayer {
        val response = importV1(VOD_URN, token)
        response shouldHaveStatus HttpStatusCode.Created

        val imported = response.body<MediaResponseV1>()
        imported.id shouldBe VOD_URN
        imported.metadata.title shouldBe "Telegiornale flash"

        val fetched =
          client
            .get("/v1/media/$VOD_URN") { bearerAuth(token) }
            .body<MediaResponseV1>()
        fetched.metadata.title shouldBe "Telegiornale flash"
      }
    }

    should("upsert when the URN is imported twice") {
      withStubbedIntegrationLayer {
        importV1(VOD_URN, token) shouldHaveStatus HttpStatusCode.Created
        importV1(VOD_URN, token) shouldHaveStatus HttpStatusCode.Created

        val page =
          client
            .get("/v1/media?visibility=active") { bearerAuth(token) }
            .body<List<MediaResponseV1>>()
        page.map { it.id } shouldBe listOf(VOD_URN)
      }
    }

    should("return BAD_GATEWAY when the URN is not found") {
      withStubbedIntegrationLayer(
        status = HttpStatusCode.NotFound,
        contentType = ContentType.parse("application/problem+json"),
        body = """{"status":404,"title":"Not found"}""",
      ) {
        importV1("urn:rts:video:unknown", token) shouldHaveStatus HttpStatusCode.BadGateway
      }
    }

    should("return BAD_REQUEST when the URN is blank") {
      testApplicationContext {
        importV1("", token) shouldHaveStatus HttpStatusCode.BadRequest
      }
    }

    should("return FORBIDDEN when authenticated without the write role") {
      testApplicationContext {
        importV1(VOD_URN, tokenWithRoles(emptySet())) shouldHaveStatus HttpStatusCode.Forbidden
      }
    }

    should("restrict re-imports to the grants of the media folder") {
      withStubbedIntegrationLayer {
        val admin = tokenWithRoles(setOf(Role.ADMIN))
        val granted = tokenFor("user-1")
        val stranger = tokenFor("user-2")
        seedUser(userFixture(oidcSub = "user-1"))

        importV1(VOD_URN, stranger) shouldHaveStatus HttpStatusCode.Created

        val folder =
          client
            .post("/v1/folder") {
              bearerAuth(admin)
              contentType(ContentType.Application.Json)
              setBody(FolderRequestV1(name = "Restricted"))
            }.body<FolderResponseV1>()

        client.post("/v1/folder/${folder.id}/media") {
          bearerAuth(admin)
          contentType(ContentType.Application.Json)
          setBody(AssignMediaRequestV1(mediaId = VOD_URN))
        } shouldHaveStatus HttpStatusCode.Created

        client.post("/v1/folder/${folder.id}/permission") {
          bearerAuth(admin)
          contentType(ContentType.Application.Json)
          setBody(FolderPermissionRequestV1(oidcSub = "user-1"))
        } shouldHaveStatus HttpStatusCode.Created

        importV1(VOD_URN, stranger) shouldHaveStatus HttpStatusCode.Forbidden
        importV1(VOD_URN, granted) shouldHaveStatus HttpStatusCode.Created
      }
    }
  })
