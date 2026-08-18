package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.api.Navigation
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.AssignMediaRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.FolderPermissionRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.FolderRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.FolderResponseV1
import ch.srgssr.pillarbox.backend.test.count
import ch.srgssr.pillarbox.backend.test.hxDelete
import ch.srgssr.pillarbox.backend.test.hxGet
import ch.srgssr.pillarbox.backend.test.hxPost
import ch.srgssr.pillarbox.backend.test.login
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.seedUser
import ch.srgssr.pillarbox.backend.test.testApplicationContext
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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jsoup.Jsoup
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

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

    should("mark expired media and hide its player URL") {
      testApplicationContext {
        login()

        val expired = mediaFixture { expiresAt = Clock.System.now() - 1.hours }
        val live = mediaFixture { expiresAt = Clock.System.now() + 1.hours }

        for (media in listOf(expired, live)) {
          client.hxPost("${Navigation.CONSOLE}/actions/media") {
            contentType(ContentType.Application.Json)
            setBody(media)
          }
        }

        val doc = Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/media-grid").bodyAsText())

        doc.count(".media-card") shouldBe 2
        doc.count("#media-card-${expired.id} .tag-list li.expired") shouldBe 1
        doc.count("#media-card-${expired.id} [data-copy-url]") shouldBe 0
        doc.count("#media-card-${live.id} .tag-list li.expired") shouldBe 0
        doc.count("#media-card-${live.id} [data-copy-url]") shouldBe 1
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

    should("return FORBIDDEN when deleting media in a folder restricted to someone else") {
      testApplicationContext {
        login()

        val media = mediaFixture()
        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        // The session cookie also authenticates against the V1 API.
        val folder =
          client
            .post("/v1/folder") {
              contentType(ContentType.Application.Json)
              setBody(FolderRequestV1(name = "Locked"))
            }.body<FolderResponseV1>()
        client.post("/v1/folder/${folder.id}/media") {
          contentType(ContentType.Application.Json)
          setBody(AssignMediaRequestV1(mediaId = media.id))
        } shouldHaveStatus HttpStatusCode.Created

        seedUser(userFixture(oidcSub = "someone-else"))
        client.post("/v1/folder/${folder.id}/permission") {
          bearerAuth(tokenWithRoles(setOf(Role.ADMIN)))
          contentType(ContentType.Application.Json)
          setBody(FolderPermissionRequestV1(oidcSub = "someone-else"))
        } shouldHaveStatus HttpStatusCode.Created

        client.hxDelete("${Navigation.CONSOLE}/actions/media/${media.id}") shouldHaveStatus
          HttpStatusCode.Forbidden
      }
    }

    should("return NOT_FOUND when deleting a non-existing media") {
      testApplicationContext {
        login()

        client.hxDelete("${Navigation.CONSOLE}/actions/media/not-a-media") shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return NOT_FOUND when an admin restores a non-existing media") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))

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
        login(roles = setOf(Role.ADMIN))
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

    should("let a WRITE user delete but reserve restoring from the bin for admins") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))

        client.hxGet("${Navigation.CONSOLE}/fragments/media-grid") shouldHaveStatus HttpStatusCode.OK
        client.hxDelete("${Navigation.CONSOLE}/actions/media/any-id") shouldHaveStatus HttpStatusCode.NotFound
        client.hxPost("${Navigation.CONSOLE}/actions/media/any-id/restore") shouldHaveStatus HttpStatusCode.Forbidden
      }
    }

    should("let an admin restore from the bin") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))

        client.hxPost("${Navigation.CONSOLE}/actions/media/any-id/restore") shouldHaveStatus HttpStatusCode.NotFound
      }
    }
  })
