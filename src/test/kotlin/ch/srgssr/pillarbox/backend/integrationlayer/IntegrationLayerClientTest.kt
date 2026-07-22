package ch.srgssr.pillarbox.backend.integrationlayer

import ch.srgssr.pillarbox.backend.test.IntegrationLayerFixtures
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private fun httpClient(engine: MockEngine) =
  HttpClient(engine) {
    install(ContentNegotiation) {
      json(Json { ignoreUnknownKeys = true })
    }
  }

class IntegrationLayerClientTest :
  ShouldSpec({

    should("fetch and decode the media composition for a URN") {
      val engine =
        MockEngine { request ->
          request.url.toString() shouldBe
            "https://il.example.ch/integrationlayer/2.0/mediaComposition/byUrn/urn:rsi:video:3845234?forceLocation=CH"
          respond(
            content = IntegrationLayerFixtures.vodComposition,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
          )
        }
      val client = IntegrationLayerClient(httpClient(engine), IntegrationLayerConfig(baseUrl = "https://il.example.ch"))

      val composition = client.findMediaComposition("urn:rsi:video:3845234")

      composition.shouldNotBeNull().chapterUrn shouldBe "urn:rsi:video:3845234"
      composition.chapterList shouldBe composition.chapterList.filter { it.urn == "urn:rsi:video:3845234" }
    }

    should("return null when the URN is not found") {
      val engine =
        MockEngine {
          respond(
            content = """{"status":404,"title":"Not found"}""",
            status = HttpStatusCode.NotFound,
            headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
          )
        }
      val client = IntegrationLayerClient(httpClient(engine), IntegrationLayerConfig(baseUrl = "https://il.example.ch"))

      client.findMediaComposition("urn:rts:video:unknown").shouldBeNull()
    }

    should("encode reserved characters in the URN and tolerate a trailing slash in the base url") {
      val engine =
        MockEngine { request ->
          request.url.toString() shouldBe
            "https://il.example.ch/integrationlayer/2.0/mediaComposition/byUrn/urn%3F%23%2F%25:video:1?forceLocation=CH"
          respond(
            content = IntegrationLayerFixtures.vodComposition,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
          )
        }
      val client =
        IntegrationLayerClient(httpClient(engine), IntegrationLayerConfig(baseUrl = "https://il.example.ch/"))

      client.findMediaComposition("urn?#/%:video:1").shouldNotBeNull()
    }
  })
