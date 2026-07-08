package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.MediaMetadata
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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jsoup.Jsoup

class MediaSearchConsoleTest :
  ShouldSpec({

    fun media(
      title: String,
      tags: List<String> = emptyList(),
    ) = mediaFixture {
      metadata = MediaMetadata(title = title)
    }.copy(tags = tags)

    suspend fun HttpClient.createMedia(media: Media) =
      hxPost("${Navigation.CONSOLE}/actions/media") {
        contentType(ContentType.Application.Json)
        setBody(media)
      }

    should("return only the media matching the query") {
      testApplicationContext {
        login()

        val alps = media("Sunrise over the Alps")
        val city = media("City nightlife")
        client.createMedia(alps)
        client.createMedia(city)

        val doc =
          Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/media-search?q=alps").bodyAsText())

        doc.count(".media-card") shouldBe 1
        doc.select("#media-card-${alps.id}").size shouldBe 1
        doc.select("#media-card-${city.id}").size shouldBe 0
      }
    }

    should("render write actions for media the user may write") {
      testApplicationContext {
        login()

        val glacier = media("Glacier hike")
        client.createMedia(glacier)

        val doc =
          Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/media-search?q=glacier").bodyAsText())

        doc.select("#media-card-${glacier.id} footer.actions").size shouldBe 1
      }
    }

    should("hide write actions for matching media the user cannot write") {
      testApplicationContext {
        login()

        val glacier = media("Glacier descent")
        client.createMedia(glacier)

        val folder =
          client
            .post("/v1/folder") {
              contentType(ContentType.Application.Json)
              setBody(FolderRequestV1(name = "Locked"))
            }.body<FolderResponseV1>()
        client.post("/v1/folder/${folder.id}/media") {
          contentType(ContentType.Application.Json)
          setBody(AssignMediaRequestV1(mediaId = glacier.id))
        } shouldHaveStatus HttpStatusCode.Created

        seedUser(userFixture(oidcSub = "someone-else"))
        client.post("/v1/folder/${folder.id}/permission") {
          bearerAuth(tokenWithRoles(setOf(Role.ADMIN)))
          contentType(ContentType.Application.Json)
          setBody(FolderPermissionRequestV1(oidcSub = "someone-else"))
        } shouldHaveStatus HttpStatusCode.Created

        val doc =
          Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/media-search?q=glacier").bodyAsText())

        doc.count(".media-card") shouldBe 1
        doc.select("#media-card-${glacier.id} footer.actions").size shouldBe 0
      }
    }

    should("label each result with its folder and point its actions at it") {
      testApplicationContext {
        login()

        val glacier = media("Glacier hike")
        client.createMedia(glacier)

        val folder =
          client
            .post("/v1/folder") {
              contentType(ContentType.Application.Json)
              setBody(FolderRequestV1(name = "Documentaries"))
            }.body<FolderResponseV1>()
        client.post("/v1/folder/${folder.id}/media") {
          contentType(ContentType.Application.Json)
          setBody(AssignMediaRequestV1(mediaId = glacier.id))
        } shouldHaveStatus HttpStatusCode.Created

        val doc =
          Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/media-search?q=glacier").bodyAsText())

        val card = doc.select("#media-card-${glacier.id}")
        card.select(".media-card-folder").text() shouldBe "folder Documentaries"
        card
          .select("a[href^='/console/editor/${glacier.id}?folderId=']")
          .attr("href") shouldBe "/console/editor/${glacier.id}?folderId=${folder.id}"
      }
    }

    should("restore the library sections when the query is cleared") {
      testApplicationContext {
        login()

        val doc =
          Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/media-search?q=").bodyAsText())

        doc.select("#folder-section").size shouldBe 1
        doc.select("#media-section").size shouldBe 1
        doc.count(".search-results") shouldBe 0
      }
    }

    should("show a search-specific empty state when nothing matches") {
      testApplicationContext {
        login()

        client.createMedia(media("Sunrise over the Alps"))

        val doc =
          Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/media-search?q=nonexistent").bodyAsText())

        doc.count(".media-card") shouldBe 0
        doc.select(".media-list[data-empty-label]").size shouldBe 1
      }
    }

    should("find soft-deleted media when searching the bin") {
      testApplicationContext {
        login()

        val glacier = media("Glacier hike")
        client.createMedia(glacier)
        client.hxDelete("${Navigation.CONSOLE}/actions/media/${glacier.id}") shouldHaveStatus HttpStatusCode.OK

        val active =
          Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/media-search?q=glacier").bodyAsText())
        active.select("#media-card-${glacier.id}").size shouldBe 0

        val bin =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/media-grid?q=glacier&deleted=true").bodyAsText(),
          )
        bin.select("#media-card-${glacier.id}").size shouldBe 1
      }
    }
  })
