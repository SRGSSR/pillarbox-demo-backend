package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.api.Navigation
import ch.srgssr.pillarbox.backend.test.count
import ch.srgssr.pillarbox.backend.test.hxDelete
import ch.srgssr.pillarbox.backend.test.hxGet
import ch.srgssr.pillarbox.backend.test.hxPost
import ch.srgssr.pillarbox.backend.test.login
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jsoup.Jsoup

class MediaGridRouteTest :
  ShouldSpec({

    should("render media grid fragment with correct number of cards") {
      testApplicationContext {
        login()

        repeat(5) {
          mediaFixture().let {
            client.hxPost("${Navigation.CONSOLE}/actions/media") {
              contentType(ContentType.Application.Json)
              setBody(it)
            }
          }
        }

        val response = client.hxGet("${Navigation.CONSOLE}/fragments/media-grid?pageSize=5")
        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())

        doc.count(".media-card") shouldBe 5
      }
    }

    should("delete existing media") {
      testApplicationContext {
        login()

        val media = mediaFixture()
        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        client.hxDelete("${Navigation.CONSOLE}/actions/media/${media.id}") shouldHaveStatus HttpStatusCode.OK
      }
    }

    should("return NOT_FOUND when deleting a non-existing media") {
      testApplicationContext {
        login()

        client.hxDelete("${Navigation.CONSOLE}/actions/media/not-a-media") shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return NOT_FOUND when restoring a non-existing media") {
      testApplicationContext {
        login()

        client.hxPost("${Navigation.CONSOLE}/actions/media/not-a-media/restore") shouldHaveStatus
          HttpStatusCode.NotFound
      }
    }

    should("show media in the deleted grid and hide it from the active grid after delete") {
      testApplicationContext {
        login()
        val media = mediaFixture()

        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        client.hxDelete("${Navigation.CONSOLE}/actions/media/${media.id}") shouldHaveStatus HttpStatusCode.OK

        val activeDoc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/media-grid?deleted=false").bodyAsText(),
          )
        activeDoc.select("#media-card-${media.id}").size shouldBe 0

        val deletedDoc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/media-grid?deleted=true").bodyAsText(),
          )
        deletedDoc.select("#media-card-${media.id}").size shouldBe 1
      }
    }

    should("show media in the active grid and hide it from the deleted grid after restoring") {
      testApplicationContext {
        login()
        val media = mediaFixture()

        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }
        client.hxDelete("${Navigation.CONSOLE}/actions/media/${media.id}")
        client.hxPost("${Navigation.CONSOLE}/actions/media/${media.id}/restore") shouldHaveStatus HttpStatusCode.OK

        val activeDoc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/media-grid?deleted=false").bodyAsText(),
          )
        activeDoc.select("#media-card-${media.id}").size shouldBe 1

        val deletedDoc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/media-grid?deleted=true").bodyAsText(),
          )
        deletedDoc.select("#media-card-${media.id}").size shouldBe 0
      }
    }

    should("allow read access but return 403 on write endpoints when authenticated with no roles") {
      testApplicationContext {
        login(roles = emptySet())

        client.hxGet("${Navigation.CONSOLE}/fragments/media-grid") shouldHaveStatus HttpStatusCode.OK
        client.hxDelete("${Navigation.CONSOLE}/actions/media/any-id") shouldHaveStatus HttpStatusCode.Forbidden
        client.hxPost("${Navigation.CONSOLE}/actions/media/any-id/restore") shouldHaveStatus HttpStatusCode.Forbidden
      }
    }

    should("allow WRITE user to access all endpoints") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))

        client.hxGet("${Navigation.CONSOLE}/fragments/media-grid") shouldHaveStatus HttpStatusCode.OK
        client.hxDelete("${Navigation.CONSOLE}/actions/media/any-id") shouldHaveStatus HttpStatusCode.NotFound
        client.hxPost("${Navigation.CONSOLE}/actions/media/any-id/restore") shouldHaveStatus HttpStatusCode.NotFound
      }
    }
  })
