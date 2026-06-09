package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.api.Navigation
import ch.srgssr.pillarbox.backend.test.count
import ch.srgssr.pillarbox.backend.test.hxDelete
import ch.srgssr.pillarbox.backend.test.hxGet
import ch.srgssr.pillarbox.backend.test.hxPatch
import ch.srgssr.pillarbox.backend.test.hxPost
import ch.srgssr.pillarbox.backend.test.login
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import org.jsoup.Jsoup

class HomeRouteTest :
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

    should("render the bin page when authenticated") {
      testApplicationContext {
        login()
        val response = client.get("${Navigation.CONSOLE}/bin")
        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())
        doc.body() shouldNotBe null
      }
    }

    should("render an empty folder grid fragment") {
      testApplicationContext {
        login()
        val response = client.hxGet("${Navigation.CONSOLE}/fragments/folder-grid")
        response shouldHaveStatus HttpStatusCode.OK

        Jsoup.parse(response.bodyAsText()).count(".folder-card") shouldBe 0
      }
    }

    should("render folder cards in the folder grid") {
      testApplicationContext {
        login()
        repeat(3) { i -> client.createFolder("Folder $i") }

        val response = client.hxGet("${Navigation.CONSOLE}/fragments/folder-grid")
        response shouldHaveStatus HttpStatusCode.OK

        Jsoup.parse(response.bodyAsText()).count(".folder-card") shouldBe 3
      }
    }

    should("render folder picker fragment with the media ID") {
      testApplicationContext {
        login()
        val media = mediaFixture()
        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        val response = client.hxGet("${Navigation.CONSOLE}/fragments/folder-picker?mediaId=${media.id}")
        response shouldHaveStatus HttpStatusCode.OK

        Jsoup.parse(response.bodyAsText()).select("#picker-media-id").attr("value") shouldBe media.id
      }
    }

    should("render an empty folder picker child fragment when no folders exist") {
      testApplicationContext {
        login()
        client.hxGet("${Navigation.CONSOLE}/fragments/folder-picker-child") shouldHaveStatus HttpStatusCode.OK
      }
    }

    should("render folder rows in folder picker child fragment") {
      testApplicationContext {
        login()
        repeat(2) { i -> client.createFolder("Folder $i") }

        val response = client.hxGet("${Navigation.CONSOLE}/fragments/folder-picker-child")
        response shouldHaveStatus HttpStatusCode.OK

        Jsoup.parse(response.bodyAsText()).count(".folder-row") shouldBe 2
      }
    }

    should("create a folder and render its card") {
      testApplicationContext {
        login()
        val response = client.createFolderResponse("Test Folder")
        response shouldHaveStatus HttpStatusCode.OK

        Jsoup.parse(response.bodyAsText()).count(".folder-card") shouldBe 1
      }
    }

    should("rename an existing folder") {
      testApplicationContext {
        login()
        val folderId = client.createFolder("Old Name")

        val response =
          client.hxPatch("${Navigation.CONSOLE}/actions/folder/$folderId") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("name" to "Renamed").formUrlEncode())
          }
        response shouldHaveStatus HttpStatusCode.OK

        Jsoup.parse(response.bodyAsText()).select(".folder-card-name").text() shouldBe "Renamed"
      }
    }

    should("return NOT_FOUND when renaming a non-existent folder") {
      testApplicationContext {
        login()
        client.hxPatch("${Navigation.CONSOLE}/actions/folder/not-a-folder") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody(listOf("name" to "Renamed").formUrlEncode())
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("delete an existing folder") {
      testApplicationContext {
        login()
        val folderId = client.createFolder("To Delete")
        client.hxDelete("${Navigation.CONSOLE}/actions/folder/$folderId") shouldHaveStatus HttpStatusCode.OK
      }
    }

    should("assign media to a folder") {
      testApplicationContext {
        login()
        val media = mediaFixture()
        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }
        val folderId = client.createFolder("Test Folder")

        val response =
          client.hxPost("${Navigation.CONSOLE}/actions/folder/$folderId/media") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("mediaID" to media.id).formUrlEncode())
          }
        response shouldHaveStatus HttpStatusCode.OK

        Jsoup.parse(response.bodyAsText()).select(".folder-card-count").text() shouldBe "1 item"
      }
    }

    should("return NOT_FOUND when assigning media to a non-existent folder") {
      testApplicationContext {
        login()
        val media = mediaFixture()
        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        client.hxPost("${Navigation.CONSOLE}/actions/folder/not-a-folder/media") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody(listOf("mediaID" to media.id).formUrlEncode())
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return UNPROCESSABLE_ENTITY when assigning a non-existent media to a folder") {
      testApplicationContext {
        login()
        val folderId = client.createFolder("Test Folder")

        client.hxPost("${Navigation.CONSOLE}/actions/folder/$folderId/media") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody(listOf("mediaID" to "non-existent-media").formUrlEncode())
        } shouldHaveStatus HttpStatusCode.UnprocessableEntity
      }
    }

    should("remove a media assignment from a folder") {
      testApplicationContext {
        login()
        val media = mediaFixture()
        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }
        val folderId = client.createFolder("Test Folder")
        client.hxPost("${Navigation.CONSOLE}/actions/folder/$folderId/media") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody(listOf("mediaID" to media.id).formUrlEncode())
        }

        client.hxDelete("${Navigation.CONSOLE}/actions/folder/$folderId/media/${media.id}") shouldHaveStatus
          HttpStatusCode.OK
      }
    }

    should("return NOT_FOUND when removing media from a non-existent folder") {
      testApplicationContext {
        login()
        client.hxDelete("${Navigation.CONSOLE}/actions/folder/not-a-folder/media/not-a-media") shouldHaveStatus
          HttpStatusCode.NotFound
      }
    }

    should("return NOT_FOUND when removing a non-existent media from a folder") {
      testApplicationContext {
        login()
        val folderId = client.createFolder("Test Folder")
        client.hxDelete("${Navigation.CONSOLE}/actions/folder/$folderId/media/not-a-media") shouldHaveStatus
          HttpStatusCode.NotFound
      }
    }

    should("allow read access but return 403 on write endpoints when authenticated with no roles") {
      testApplicationContext {
        login(roles = emptySet())

        client.get(Navigation.CONSOLE) shouldHaveStatus HttpStatusCode.OK
        client.get("${Navigation.CONSOLE}/bin") shouldHaveStatus HttpStatusCode.OK
        client.hxGet("${Navigation.CONSOLE}/fragments/folder-grid") shouldHaveStatus HttpStatusCode.OK
        client.hxGet("${Navigation.CONSOLE}/fragments/folder-picker") shouldHaveStatus HttpStatusCode.BadRequest
        client.hxGet("${Navigation.CONSOLE}/fragments/folder-picker-child") shouldHaveStatus HttpStatusCode.OK
        client.hxPost("${Navigation.CONSOLE}/actions/folder") shouldHaveStatus HttpStatusCode.Forbidden
        client.hxPatch("${Navigation.CONSOLE}/actions/folder/any-id") shouldHaveStatus HttpStatusCode.Forbidden
        client.hxDelete("${Navigation.CONSOLE}/actions/folder/any-id") shouldHaveStatus HttpStatusCode.Forbidden
        client.hxPost("${Navigation.CONSOLE}/actions/folder/any-id/media") shouldHaveStatus HttpStatusCode.Forbidden
        client.hxDelete("${Navigation.CONSOLE}/actions/folder/any-id/media/any-id") shouldHaveStatus
          HttpStatusCode.Forbidden
      }
    }

    should("allow WRITE user to access all endpoints") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))

        client.get(Navigation.CONSOLE) shouldHaveStatus HttpStatusCode.OK
        client.get("${Navigation.CONSOLE}/bin") shouldHaveStatus HttpStatusCode.OK
        client.hxGet("${Navigation.CONSOLE}/fragments/folder-grid") shouldHaveStatus HttpStatusCode.OK
        client.hxGet("${Navigation.CONSOLE}/fragments/folder-picker") shouldHaveStatus HttpStatusCode.BadRequest
        client.hxGet("${Navigation.CONSOLE}/fragments/folder-picker-child") shouldHaveStatus HttpStatusCode.OK
        client.hxPost("${Navigation.CONSOLE}/actions/folder") shouldHaveStatus HttpStatusCode.UnsupportedMediaType
        client.hxPatch("${Navigation.CONSOLE}/actions/folder/any-id") shouldHaveStatus
          HttpStatusCode.UnsupportedMediaType
        client.hxDelete("${Navigation.CONSOLE}/actions/folder/any-id") shouldHaveStatus HttpStatusCode.NotFound
        client.hxPost("${Navigation.CONSOLE}/actions/folder/any-id/media") shouldHaveStatus
          HttpStatusCode.UnsupportedMediaType
        client.hxDelete("${Navigation.CONSOLE}/actions/folder/any-id/media/any-id") shouldHaveStatus
          HttpStatusCode.NotFound
      }
    }
  })

private suspend fun HttpClient.createFolderResponse(name: String): HttpResponse =
  hxPost("${Navigation.CONSOLE}/actions/folder") {
    contentType(ContentType.Application.FormUrlEncoded)
    setBody(listOf("name" to name).formUrlEncode())
  }

private suspend fun HttpClient.createFolder(name: String): String =
  Jsoup
    .parse(createFolderResponse(name).bodyAsText())
    .select(".folder-card")
    .first()
    ?.id()
    ?.removePrefix("folder-card-")
    ?: error("No folder card rendered for '$name'")
