package ch.srgssr.pillarbox.backend.entrypoint.web

import ch.srgssr.pillarbox.backend.domain.model.MediaMetadata
import ch.srgssr.pillarbox.backend.test.MediaLibrary
import ch.srgssr.pillarbox.backend.test.count
import ch.srgssr.pillarbox.backend.test.get
import ch.srgssr.pillarbox.backend.test.hxDelete
import ch.srgssr.pillarbox.backend.test.hxGet
import ch.srgssr.pillarbox.backend.test.hxPost
import ch.srgssr.pillarbox.backend.test.login
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldBeBlank
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jsoup.Jsoup

class ConsoleRouteTest :
  ShouldSpec({

    should("render home page when authenticated") {
      testApplicationContext {
        login()
        val response = client.get(Navigation.CONSOLE)
        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())
        doc.body() shouldNotBe null
      }
    }

    should("delete existing media") {
      testApplicationContext {
        login()

        val media = mediaFixture()
        client.hxPost("${Navigation.CONSOLE}/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        client.hxDelete("${Navigation.CONSOLE}/media/${media.id}") {
        } shouldHaveStatus HttpStatusCode.OK
      }
    }

    should("return NOT_FOUND when deleting a non-existing media") {
      testApplicationContext {
        login()

        client.hxDelete("${Navigation.CONSOLE}/media/not-a-media") {
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("render media grid fragment with correct number of cards") {
      testApplicationContext {
        login()

        repeat(5) {
          mediaFixture().let {
            client.hxPost("${Navigation.CONSOLE}/media") {
              contentType(ContentType.Application.Json)
              setBody(it)
            }
          }
        }

        val response = client.hxGet("${Navigation.CONSOLE}/media?pageSize=5")
        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())

        doc.count(".media-card") shouldBe 5
      }
    }

    should("render an empty editor form for a new media") {
      testApplicationContext {
        login()

        val response = client.get("${Navigation.CONSOLE}/media/editor/")
        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())

        doc["input[name='metadata.title']"].first()?.attributes()["value"].shouldBeBlank()
        doc["input[name='metadata.subtitle']"].first()?.attributes()["value"].shouldBeBlank()
        doc["input[name='id']"].first()?.attributes()["value"].shouldBeBlank()
      }
    }

    should("populate editor form with existing media data") {
      testApplicationContext {
        login()
        val media =
          mediaFixture {
            this.metadata = MediaMetadata(title = "Test Title", subtitle = "Test Subtitle")
            withDash(MediaLibrary.Widevine)
            withSubtitles()
            withIntro()
            withChapters()
          }

        client.hxPost("${Navigation.CONSOLE}/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        val response = client.get("${Navigation.CONSOLE}/media/editor/${media.id}")
        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())

        doc["input[name='metadata.title']"].first()?.attributes()["value"] shouldBe media.metadata.title
        doc["input[name='metadata.subtitle']"].first()?.attributes()["value"] shouldBe media.metadata.subtitle
        doc["input[name='id']"].first()?.attributes()["value"] shouldBe media.id
      }
    }

    should("populate editor form with existing media data for duplication") {
      testApplicationContext {
        login()
        val media =
          mediaFixture {
            this.metadata = MediaMetadata(title = "Test Title", subtitle = "Test Subtitle")
            withDash(MediaLibrary.Widevine)
            withSubtitles()
            withIntro()
            withChapters()
          }

        client.hxPost("${Navigation.CONSOLE}/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        val response = client.get("${Navigation.CONSOLE}/media/editor/${media.id}/duplicate")
        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())

        doc["input[name='metadata.title']"].first()?.attributes()["value"] shouldBe media.metadata.title
        doc["input[name='metadata.subtitle']"].first()?.attributes()["value"] shouldBe media.metadata.subtitle
        doc["input[name='id']"].first()?.attributes()["value"].shouldBeBlank()
      }
    }

    should("render editor fragment with correct index") {
      testApplicationContext {
        login()

        val index = 42
        val response = client.hxGet("${Navigation.CONSOLE}/media/editor/fragments/chapter?index=$index")

        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())

        doc["input"].shouldForAll { it.attributes()["name"].contains("[$index]") }
      }
    }

    should("return NOT_FOUND for invalid editor fragment") {
      testApplicationContext {
        login()

        val response = client.hxGet("${Navigation.CONSOLE}/media/editor/fragments/invalid-type")

        response shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("render all allowed editor fragments") {
      testApplicationContext {
        login()

        val fragments = listOf("chapter", "time-range", "source", "subtitle", "drm")

        fragments.forEach { slug ->
          val response = client.hxGet("${Navigation.CONSOLE}/media/editor/fragments/$slug")
          response shouldHaveStatus HttpStatusCode.OK

          val doc = Jsoup.parse(response.bodyAsText())

          doc[".entry-item"].shouldNotBeEmpty()
        }
      }
    }
  })
