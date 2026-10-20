package ch.srgssr.pillarbox.backend.adapter.web.console

import ch.srgssr.pillarbox.backend.adapter.web.api.Navigation
import ch.srgssr.pillarbox.backend.adapter.web.http.FieldErrors
import ch.srgssr.pillarbox.backend.domain.model.MediaMetadata
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.test.MediaLibrary
import ch.srgssr.pillarbox.backend.test.get
import ch.srgssr.pillarbox.backend.test.hxGet
import ch.srgssr.pillarbox.backend.test.hxPost
import ch.srgssr.pillarbox.backend.test.hxPut
import ch.srgssr.pillarbox.backend.test.login
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeBlank
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jsoup.Jsoup
import kotlin.time.Instant

class EditorRouteTest :
  ShouldSpec({

    should("render an empty editor form for a new media") {
      testApplicationContext {
        login()

        val response = client.get("${Navigation.CONSOLE}/editor/")
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

        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        val response = client.get("${Navigation.CONSOLE}/editor/${media.id}")
        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())

        doc["input[name='metadata.title']"].first()?.attributes()["value"] shouldBe media.metadata.title
        doc["input[name='metadata.subtitle']"].first()?.attributes()["value"] shouldBe media.metadata.subtitle
        doc["input[name='id']"].first()?.attributes()["value"] shouldBe media.id
      }
    }

    should("populate the expiry field in the display time zone") {
      testApplicationContext {
        login()
        // 12:32 UTC in June is 14:32 in Zurich, which is what the control must show.
        val media = mediaFixture { expiresAt = Instant.parse("2026-06-06T12:32:00Z") }

        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        val response = client.get("${Navigation.CONSOLE}/editor/${media.id}")
        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())
        val expiresAt = doc["input[name='expiresAt']"].first()

        expiresAt?.attributes()?.get("type") shouldBe "datetime-local"
        expiresAt?.attributes()?.get("value") shouldBe "2026-06-06T14:32"
        expiresAt?.attributes()?.get("data-time-zone") shouldBe "Europe/Zurich"
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

        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        val response = client.get("${Navigation.CONSOLE}/editor/${media.id}/duplicate")
        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())

        doc["input[name='metadata.title']"].first()?.attributes()["value"] shouldBe media.metadata.title
        doc["input[name='metadata.subtitle']"].first()?.attributes()["value"] shouldBe media.metadata.subtitle
        doc["input[name='id']"].first()?.attributes()["value"].shouldBeBlank()
      }
    }

    should("render the media ID as editable and target the create endpoint for a new media") {
      testApplicationContext {
        login()

        val doc = Jsoup.parse(client.get("${Navigation.CONSOLE}/editor/").bodyAsText())

        doc["input[name='id']"].first()?.hasAttr("readonly") shouldBe false
        doc["form.media-form"].first()?.attributes()["hx-post"] shouldBe "${Navigation.CONSOLE}/actions/media"
      }
    }

    should("render the media ID as read-only and target the update endpoint for an existing media") {
      testApplicationContext {
        login()
        val media = mediaFixture()

        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        val doc = Jsoup.parse(client.get("${Navigation.CONSOLE}/editor/${media.id}").bodyAsText())

        doc["input[name='id']"].first()?.hasAttr("readonly") shouldBe true
        doc["form.media-form"].first()?.attributes()["hx-put"] shouldBe
          "${Navigation.CONSOLE}/actions/media/${media.id}"
      }
    }

    should("reject creating a media whose ID already exists") {
      testApplicationContext {
        login()
        val media = mediaFixture { metadata = MediaMetadata(title = "Original") }

        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        } shouldHaveStatus HttpStatusCode.OK

        val conflict =
          client.hxPost("${Navigation.CONSOLE}/actions/media") {
            contentType(ContentType.Application.Json)
            setBody(media.copy(metadata = MediaMetadata(title = "Overwritten")))
          }
        conflict shouldHaveStatus HttpStatusCode.Conflict
        conflict.body<FieldErrors>() shouldBe FieldErrors(mapOf("id" to "A media with this ID already exists"))

        val doc = Jsoup.parse(client.get("${Navigation.CONSOLE}/editor/${media.id}").bodyAsText())
        doc["input[name='metadata.title']"].first()?.attributes()["value"] shouldBe "Original"
      }
    }

    should("update an existing media through its ID in the path") {
      testApplicationContext {
        login()
        val media = mediaFixture { metadata = MediaMetadata(title = "Original") }

        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        client.hxPut("${Navigation.CONSOLE}/actions/media/${media.id}") {
          contentType(ContentType.Application.Json)
          setBody(media.copy(metadata = MediaMetadata(title = "Updated")))
        } shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(client.get("${Navigation.CONSOLE}/editor/${media.id}").bodyAsText())
        doc["input[name='metadata.title']"].first()?.attributes()["value"] shouldBe "Updated"
      }
    }

    should("keep the path ID when the update body carries a different ID") {
      testApplicationContext {
        login()
        val media = mediaFixture { metadata = MediaMetadata(title = "Original") }

        client.hxPost("${Navigation.CONSOLE}/actions/media") {
          contentType(ContentType.Application.Json)
          setBody(media)
        }

        client.hxPut("${Navigation.CONSOLE}/actions/media/${media.id}") {
          contentType(ContentType.Application.Json)
          setBody(media.copy(id = "renamed-id", metadata = MediaMetadata(title = "Updated")))
        } shouldHaveStatus HttpStatusCode.OK

        val original = Jsoup.parse(client.get("${Navigation.CONSOLE}/editor/${media.id}").bodyAsText())
        original["input[name='metadata.title']"].first()?.attributes()["value"] shouldBe "Updated"

        val renamed = Jsoup.parse(client.get("${Navigation.CONSOLE}/editor/renamed-id").bodyAsText())
        renamed["input[name='id']"].first()?.attributes()["value"].shouldBeBlank()
      }
    }

    should("return NOT_FOUND when updating a media that does not exist") {
      testApplicationContext {
        login()

        client.hxPut("${Navigation.CONSOLE}/actions/media/not-a-media") {
          contentType(ContentType.Application.Json)
          setBody(mediaFixture { id = "not-a-media" })
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("render editor fragment with correct index") {
      testApplicationContext {
        login()

        val index = 42
        val response = client.hxGet("${Navigation.CONSOLE}/fragments/editor/chapter?index=$index")

        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())

        doc["input"].shouldForAll { it.attributes()["name"].contains("[$index]") }
      }
    }

    should("render DRM fragment with sourceIndex and drmIndex in all field names") {
      testApplicationContext {
        login()

        val sourceIndex = 2
        val drmIndex = 1
        val response =
          client.hxGet(
            "${Navigation.CONSOLE}/fragments/editor/drm?sourceIndex=$sourceIndex&index=$drmIndex",
          )

        response shouldHaveStatus HttpStatusCode.OK

        val doc = Jsoup.parse(response.bodyAsText())

        doc["[name]"].shouldForAll { el ->
          val name = el.attributes()["name"]
          name.contains("sources[$sourceIndex]") && name.contains("drmConfigs[$drmIndex]")
        }
      }
    }

    should("return NOT_FOUND for invalid editor fragment") {
      testApplicationContext {
        login()

        val response = client.hxGet("${Navigation.CONSOLE}/fragments/editor/invalid-type")

        response shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("render all allowed editor fragments") {
      testApplicationContext {
        login()

        val fragments = listOf("chapter", "time-range", "source", "subtitle", "drm")

        fragments.forEach { slug ->
          val response = client.hxGet("${Navigation.CONSOLE}/fragments/editor/$slug")
          response shouldHaveStatus HttpStatusCode.OK

          val doc = Jsoup.parse(response.bodyAsText())

          doc[".entry-item"].shouldNotBeEmpty()
        }
      }
    }

    should("return 403 on write endpoints when authenticated with no roles") {
      testApplicationContext {
        login(roles = emptySet())

        client.hxGet("${Navigation.CONSOLE}/editor") shouldHaveStatus HttpStatusCode.Forbidden
        client.hxGet("${Navigation.CONSOLE}/editor/any-id") shouldHaveStatus HttpStatusCode.Forbidden
        client.hxGet("${Navigation.CONSOLE}/editor/any-id/duplicate") shouldHaveStatus HttpStatusCode.Forbidden
        client.hxGet("${Navigation.CONSOLE}/fragments/editor/drm") shouldHaveStatus HttpStatusCode.Forbidden
        client.hxPost("${Navigation.CONSOLE}/actions/media") shouldHaveStatus HttpStatusCode.Forbidden
      }
    }

    should("allow WRITE user to access all endpoints") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))

        client.hxGet("${Navigation.CONSOLE}/editor") shouldHaveStatus HttpStatusCode.OK
        client.hxGet("${Navigation.CONSOLE}/editor/any-id") shouldHaveStatus HttpStatusCode.OK
        client.hxGet("${Navigation.CONSOLE}/editor/any-id/duplicate") shouldHaveStatus HttpStatusCode.NotFound
        client.hxGet("${Navigation.CONSOLE}/fragments/editor/drm") shouldHaveStatus HttpStatusCode.OK
        client.hxPost("${Navigation.CONSOLE}/actions/media") shouldHaveStatus HttpStatusCode.UnsupportedMediaType
      }
    }
  })
