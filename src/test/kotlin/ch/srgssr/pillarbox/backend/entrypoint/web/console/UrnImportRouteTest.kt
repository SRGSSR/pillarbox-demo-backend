package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.MediaMetadata
import ch.srgssr.pillarbox.backend.entrypoint.web.api.Navigation
import ch.srgssr.pillarbox.backend.test.IntegrationLayerFixtures
import ch.srgssr.pillarbox.backend.test.get
import ch.srgssr.pillarbox.backend.test.hxPost
import ch.srgssr.pillarbox.backend.test.login
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
import org.jsoup.Jsoup

/**
 * Runs a test application against a local stub of the Integration Layer that
 * answers every URN lookup with the given status and body.
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

class UrnImportRouteTest :
  ShouldSpec({

    should("prefill the editor with data imported from the Integration Layer") {
      withStubbedIntegrationLayer {
        login()

        val response = client.get("${Navigation.CONSOLE}/editor/import?urn=urn:rsi:video:3845234")
        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())

        doc["input[name='id']"].first()?.attributes()["value"] shouldBe "urn:rsi:video:3845234"
        doc["input[name='metadata.title']"].first()?.attributes()["value"] shouldBe "Telegiornale flash"
        doc["input[name='metadata.subtitle']"].first()?.attributes()["value"] shouldBe "Telegiornale"
        doc["input[name='sources[0].url']"].first()?.attributes()["value"] shouldBe
          "https://rsivod.akamaized.net/out/v1/telegiornale/index.m3u8"
        doc["input[name='metadata.chapters[0].title']"].shouldNotBeEmpty()
        doc["input[name='metadata.subtitles[0].url']"].shouldNotBeEmpty()
      }
    }

    should("prefill DRM configurations for protected streams") {
      withStubbedIntegrationLayer(body = IntegrationLayerFixtures.liveDrmComposition) {
        login()

        val response = client.get("${Navigation.CONSOLE}/editor/import?urn=urn:rts:video:3608506")
        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())

        doc["input[name='sources[0].type']"].first()?.attributes()["value"] shouldBe "DVR"
        doc["input[name='sources[0].drmConfigs[0].keySystem']"].first()?.attributes()["value"] shouldBe "com.apple.fps"
        doc["input[name='sources[1].drmConfigs[0].keySystem']"].first()?.attributes()["value"] shouldBe
          "com.widevine.alpha"
      }
    }

    should("return BAD_GATEWAY when the URN is not found") {
      withStubbedIntegrationLayer(
        status = HttpStatusCode.NotFound,
        contentType = ContentType.parse("application/problem+json"),
        body = """{"status":404,"title":"Not found"}""",
      ) {
        login()

        client.get("${Navigation.CONSOLE}/editor/import?urn=urn:rts:video:unknown") shouldHaveStatus
          HttpStatusCode.BadGateway
      }
    }

    should("return BAD_REQUEST when the URN parameter is missing") {
      testApplicationContext {
        login()

        client.get("${Navigation.CONSOLE}/editor/import") shouldHaveStatus HttpStatusCode.BadRequest
        client.get("${Navigation.CONSOLE}/editor/import?urn=") shouldHaveStatus HttpStatusCode.BadRequest
      }
    }

    should("return 403 when authenticated with no roles") {
      testApplicationContext {
        login(roles = emptySet())

        client.get("${Navigation.CONSOLE}/editor/import?urn=urn:rts:video:1") shouldHaveStatus
          HttpStatusCode.Forbidden
      }
    }

    should("label the submit button Publish until the imported media exists in the database") {
      withStubbedIntegrationLayer {
        login()

        val importResponse = client.get("${Navigation.CONSOLE}/editor/import?urn=urn:rsi:video:3845234")
        Jsoup
          .parse(importResponse.bodyAsText())[".editor-toolbar .btn-primary"]
          .first()
          ?.text() shouldBe "Publish"

        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(Media(id = "urn:rsi:video:3845234", metadata = MediaMetadata(title = "Telegiornale flash")))
        } shouldHaveStatus HttpStatusCode.OK

        val reimportResponse = client.get("${Navigation.CONSOLE}/editor/import?urn=urn:rsi:video:3845234")
        Jsoup
          .parse(reimportResponse.bodyAsText())[".editor-toolbar .btn-primary"]
          .first()
          ?.text() shouldBe "Save Changes"
      }
    }

    should("save an imported media through the regular editor flow") {
      withStubbedIntegrationLayer {
        login()

        val importResponse = client.get("${Navigation.CONSOLE}/editor/import?urn=urn:rsi:video:3845234")
        val doc = Jsoup.parse(importResponse.bodyAsText())
        val id = doc["input[name='id']"].first()?.attributes()["value"] ?: ""
        val title = doc["input[name='metadata.title']"].first()?.attributes()["value"]

        val saveResponse =
          client.hxPost("${Navigation.CONSOLE}/actions/media") {
            contentType(ContentType.Application.Json)
            setBody(Media(id = id, metadata = MediaMetadata(title = title)))
          }
        saveResponse shouldHaveStatus HttpStatusCode.OK

        val editorResponse = client.get("${Navigation.CONSOLE}/editor/$id")
        editorResponse shouldHaveStatus HttpStatusCode.OK
        Jsoup
          .parse(editorResponse.bodyAsText())["input[name='metadata.title']"]
          .first()
          ?.attributes()["value"] shouldBe "Telegiornale flash"
      }
    }
  })
