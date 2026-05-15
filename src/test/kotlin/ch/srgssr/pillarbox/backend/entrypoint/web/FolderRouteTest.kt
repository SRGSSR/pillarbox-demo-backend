package ch.srgssr.pillarbox.backend.entrypoint.web

import ch.srgssr.pillarbox.backend.entrypoint.web.dto.AssignMediaRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.FolderRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.FolderResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.MediaResponseV1
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.toMediaRequestV1
import ch.srgssr.pillarbox.backend.test.token
import ch.srgssr.pillarbox.backend.test.tokenWithRoles
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class FolderRouteTest :
  ShouldSpec({
    should("return an empty list if no folders are stored") {
      testApplicationContext {
        val response = client.get("/v1/folder") { bearerAuth(token) }

        response shouldHaveStatus HttpStatusCode.OK
        response.body<List<FolderResponseV1>>().shouldBeEmpty()
      }
    }

    should("create, update and delete a folder") {
      testApplicationContext {
        val request = FolderRequestV1(name = "Test Folder")

        val created =
          client
            .post("/v1/folder") {
              bearerAuth(token)
              contentType(ContentType.Application.Json)
              setBody(request)
            }.also { it shouldHaveStatus HttpStatusCode.Created }
            .body<FolderResponseV1>()

        created.name shouldBe request.name

        client.patch("/v1/folder/${created.id}") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(FolderRequestV1(name = "Renamed Folder"))
        } shouldHaveStatus HttpStatusCode.Created

        client
          .get("/v1/folder/${created.id}") {
            bearerAuth(token)
          }.body<FolderResponseV1>()
          .name shouldBe "Renamed Folder"

        client.delete("/v1/folder/${created.id}") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NoContent

        client.get("/v1/folder/${created.id}") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return paginated folders correctly") {
      testApplicationContext {
        repeat(20) {
          client.post("/v1/folder") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(FolderRequestV1(name = "Test Folder $it"))
          } shouldHaveStatus HttpStatusCode.Created
        }

        client.getFolderPageV1(limit = 5, offset = 0) { bearerAuth(token) }.size shouldBe 5
        client.getFolderPageV1(limit = 1, offset = 5) { bearerAuth(token) }.size shouldBe 1
        client.getFolderPageV1(limit = 1, offset = 20) { bearerAuth(token) }.size shouldBe 0
      }
    }

    should("filter by parent id child folders") {
      testApplicationContext {
        val parent =
          client
            .post("/v1/folder") {
              bearerAuth(token)
              contentType(ContentType.Application.Json)
              setBody(FolderRequestV1(name = "Parent Folder"))
            }.also { it shouldHaveStatus HttpStatusCode.Created }
            .body<FolderResponseV1>()

        repeat(5) {
          client.post("/v1/folder") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(FolderRequestV1(name = "Test Folder $it", parentId = parent.id))
          } shouldHaveStatus HttpStatusCode.Created
        }

        val folders =
          client
            .get("/v1/folder") {
              bearerAuth(token)
              url {
                parameters.append("parentId", parent.id)
              }
            }.body<List<FolderResponseV1>>()

        folders.size shouldBe 5
      }
    }

    should("return NOT_FOUND when getting a non-existent folder") {
      testApplicationContext {
        client.get("/v1/folder/does-not-exist") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return NOT_FOUND when getting media for a non-existent folder") {
      testApplicationContext {
        client.get("/v1/folder/does-not-exist/media") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return NOT_FOUND when patching a non-existent folder") {
      testApplicationContext {
        client.patch("/v1/folder/does-not-exist") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(FolderRequestV1(name = "Test Folder"))
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return NOT_FOUND when deleting a non-existent folder") {
      testApplicationContext {
        client.delete("/v1/folder/does-not-exist") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("assign a media to a folder and list it") {
      testApplicationContext {
        val folder =
          client
            .post("/v1/folder") {
              bearerAuth(token)
              contentType(ContentType.Application.Json)
              setBody(FolderRequestV1(name = "Test Folder"))
            }.body<FolderResponseV1>()

        val media = mediaFixture()
        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        client.post("/v1/folder/${folder.id}/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(AssignMediaRequestV1(mediaId = media.id))
        } shouldHaveStatus HttpStatusCode.Created

        val folderMedia =
          client
            .get("/v1/folder/${folder.id}/media") { bearerAuth(token) }
            .body<List<MediaResponseV1>>()

        folderMedia.size shouldBe 1
        folderMedia.first().id shouldBe media.id
      }
    }

    should("remove a media assignment from a folder") {
      testApplicationContext {
        val folder =
          client
            .post("/v1/folder") {
              bearerAuth(token)
              contentType(ContentType.Application.Json)
              setBody(FolderRequestV1(name = "Test Folder"))
            }.body<FolderResponseV1>()

        val media = mediaFixture()
        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        client.post("/v1/folder/${folder.id}/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(AssignMediaRequestV1(mediaId = media.id))
        } shouldHaveStatus HttpStatusCode.Created

        client.delete("/v1/folder/${folder.id}/media/${media.id}") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NoContent

        client
          .get("/v1/folder/${folder.id}/media") { bearerAuth(token) }
          .body<List<MediaResponseV1>>()
          .shouldBeEmpty()
      }
    }

    should("return NOT_FOUND when assigning media to a non-existent folder") {
      testApplicationContext {
        val media = mediaFixture()
        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        client.post("/v1/folder/does-not-exist/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(AssignMediaRequestV1(mediaId = media.id))
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return UNPROCESSABLE_ENTITY when assigning a non-existent media to a folder") {
      testApplicationContext {
        val folder =
          client
            .post("/v1/folder") {
              bearerAuth(token)
              contentType(ContentType.Application.Json)
              setBody(FolderRequestV1(name = "Test Folder"))
            }.body<FolderResponseV1>()

        client.post("/v1/folder/${folder.id}/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(AssignMediaRequestV1(mediaId = "non-existent-media"))
        } shouldHaveStatus HttpStatusCode.UnprocessableEntity
      }
    }

    should("return NOT_FOUND when removing media from a non-existent folder") {
      testApplicationContext {
        val media = mediaFixture()
        client.post("/v1/media") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        client.delete("/v1/folder/does-not-exist/media/${media.id}") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return NOT_FOUND when removing a non-existent media from a folder") {
      testApplicationContext {
        val folder =
          client
            .post("/v1/folder") {
              bearerAuth(token)
              contentType(ContentType.Application.Json)
              setBody(FolderRequestV1(name = "Test Folder"))
            }.body<FolderResponseV1>()

        client.delete("/v1/folder/${folder.id}/media/does-not-exist") {
          bearerAuth(token)
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return 401 on all endpoints when no token is provided") {
      testApplicationContext {
        client.get("/v1/folder") shouldHaveStatus HttpStatusCode.Unauthorized
        client.get("/v1/folder/any-id") shouldHaveStatus HttpStatusCode.Unauthorized
        client.get("/v1/folder/any-id/media") shouldHaveStatus HttpStatusCode.Unauthorized
        client.post("/v1/folder") { contentType(ContentType.Application.Json) } shouldHaveStatus
          HttpStatusCode.Unauthorized
        client.patch("/v1/folder/any-id") { contentType(ContentType.Application.Json) } shouldHaveStatus
          HttpStatusCode.Unauthorized
        client.delete("/v1/folder/any-id") shouldHaveStatus HttpStatusCode.Unauthorized
        client.post("/v1/folder/any-id/media") { contentType(ContentType.Application.Json) } shouldHaveStatus
          HttpStatusCode.Unauthorized
        client.delete("/v1/folder/any-id/media/any-media-id") shouldHaveStatus HttpStatusCode.Unauthorized
      }
    }

    should("allow read access but return 403 on write endpoints when authenticated with no roles") {
      testApplicationContext {
        val t = tokenWithRoles(emptySet())

        client.get("/v1/folder") { bearerAuth(t) } shouldHaveStatus HttpStatusCode.OK
        client.get("/v1/folder/any-id") { bearerAuth(t) } shouldHaveStatus HttpStatusCode.NotFound
        client.get("/v1/folder/any-id/media") { bearerAuth(t) } shouldHaveStatus HttpStatusCode.NotFound
        client.post("/v1/folder") {
          bearerAuth(t)
          contentType(ContentType.Application.Json)
          setBody(FolderRequestV1(name = "Test Folder"))
        } shouldHaveStatus HttpStatusCode.Forbidden
        client.patch("/v1/folder/any-id") {
          bearerAuth(t)
          contentType(ContentType.Application.Json)
          setBody(FolderRequestV1(name = "Test Folder"))
        } shouldHaveStatus HttpStatusCode.Forbidden
        client.delete("/v1/folder/any-id") { bearerAuth(t) } shouldHaveStatus HttpStatusCode.Forbidden
        client.post("/v1/folder/any-id/media") {
          bearerAuth(t)
          contentType(ContentType.Application.Json)
        } shouldHaveStatus HttpStatusCode.Forbidden
        client.delete("/v1/folder/any-id/media/any-media-id") { bearerAuth(t) } shouldHaveStatus
          HttpStatusCode.Forbidden
      }
    }

    should("allow WRITE token to access all endpoints") {
      testApplicationContext {
        val folder =
          client
            .post("/v1/folder") {
              bearerAuth(token)
              contentType(ContentType.Application.Json)
              setBody(FolderRequestV1(name = "Test Folder"))
            }.body<FolderResponseV1>()

        client.get("/v1/folder") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.OK
        client.get("/v1/folder/${folder.id}") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.OK
        client.get("/v1/folder/${folder.id}/media") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.OK
        client.patch("/v1/folder/${folder.id}") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(FolderRequestV1(name = "Updated"))
        } shouldHaveStatus HttpStatusCode.Created
        client.delete("/v1/folder/${folder.id}") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.NoContent
      }
    }
  })

suspend fun HttpClient.getFolderPageV1(
  limit: Int,
  offset: Int,
  block: HttpRequestBuilder.() -> Unit = {},
): List<FolderResponseV1> =
  get("/v1/folder") {
    url {
      parameters.append("limit", limit.toString())
      parameters.append("offset", offset.toString())
    }
    block()
  }.body()
