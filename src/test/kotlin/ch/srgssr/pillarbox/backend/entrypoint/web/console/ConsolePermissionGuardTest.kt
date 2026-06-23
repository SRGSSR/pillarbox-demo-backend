package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.entrypoint.web.api.Navigation
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.AssignMediaRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.FolderPermissionRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.FolderRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.FolderResponseV1
import ch.srgssr.pillarbox.backend.test.count
import ch.srgssr.pillarbox.backend.test.hxGet
import ch.srgssr.pillarbox.backend.test.login
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.seedUser
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.userFixture
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jsoup.Jsoup

class ConsolePermissionGuardTest :
  ShouldSpec({
    should("show folder actions only on folders the editor can write") {
      testApplicationContext {
        login()
        seedUser(userFixture(oidcSub = "other"))

        val writable = client.createFolderV1("Writable")
        val restricted = client.createFolderV1("Restricted")
        client.restrictTo(restricted.id, "other")

        val doc = Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/folder-grid").bodyAsText())

        doc.count("#folder-menu-${writable.id}") shouldBe 1
        doc.count("#folder-menu-${restricted.id}") shouldBe 0
      }
    }

    should("hide media actions when the media's folder is restricted") {
      testApplicationContext {
        login()
        seedUser(userFixture(oidcSub = "other"))

        val folder = client.createFolderV1("Restricted")
        val media = mediaFixture()
        client.post("/v1/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        } shouldHaveStatus HttpStatusCode.Created
        client.assign(folder.id, media.id)
        client.restrictTo(folder.id, "other")

        val doc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/media-grid?folderId=${folder.id}").bodyAsText(),
          )

        doc.count(".media-card") shouldBe 1
        doc.count(".media-card .actions") shouldBe 0
      }
    }

    should("show media actions in a folder the editor can write") {
      testApplicationContext {
        login()

        val folder = client.createFolderV1("Writable")
        val media = mediaFixture()
        client.post("/v1/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        } shouldHaveStatus HttpStatusCode.Created
        client.assign(folder.id, media.id)

        val doc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/media-grid?folderId=${folder.id}").bodyAsText(),
          )

        doc.count(".media-card .actions") shouldBe 1
      }
    }

    should("hide the New menu when the current folder is restricted") {
      testApplicationContext {
        login()
        seedUser(userFixture(oidcSub = "other"))

        val folder = client.createFolderV1("Restricted")
        client.restrictTo(folder.id, "other")

        Jsoup
          .parse(client.get("${Navigation.CONSOLE}?folderId=${folder.id}").bodyAsText())
          .count("#new-menu") shouldBe 0
        Jsoup
          .parse(client.get(Navigation.CONSOLE).bodyAsText())
          .count("#new-menu") shouldBe 1
      }
    }

    should("return FORBIDDEN when opening the editor for a restricted folder or media") {
      testApplicationContext {
        login()
        seedUser(userFixture(oidcSub = "other"))

        val folder = client.createFolderV1("Restricted")
        val media = mediaFixture()
        client.post("/v1/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        } shouldHaveStatus HttpStatusCode.Created
        client.assign(folder.id, media.id)
        client.restrictTo(folder.id, "other")

        client.get("${Navigation.CONSOLE}/editor?folderId=${folder.id}") shouldHaveStatus HttpStatusCode.Forbidden
        client.get("${Navigation.CONSOLE}/editor/${media.id}") shouldHaveStatus HttpStatusCode.Forbidden
        client.get("${Navigation.CONSOLE}/editor/${media.id}/duplicate?folderId=${folder.id}") shouldHaveStatus
          HttpStatusCode.Forbidden
      }
    }
  })

private suspend fun HttpClient.createFolderV1(name: String): FolderResponseV1 =
  post("/v1/folder") {
    contentType(ContentType.Application.Json)
    setBody(FolderRequestV1(name = name))
  }.also { it shouldHaveStatus HttpStatusCode.Created }
    .body()

private suspend fun HttpClient.assign(
  folderId: String,
  mediaId: String,
) = post("/v1/folder/$folderId/media") {
  contentType(ContentType.Application.Json)
  setBody(AssignMediaRequestV1(mediaId = mediaId))
} shouldHaveStatus HttpStatusCode.Created

private suspend fun HttpClient.restrictTo(
  folderId: String,
  oidcSub: String,
) = post("/v1/folder/$folderId/permission") {
  contentType(ContentType.Application.Json)
  setBody(FolderPermissionRequestV1(oidcSub = oidcSub))
} shouldHaveStatus HttpStatusCode.Created
