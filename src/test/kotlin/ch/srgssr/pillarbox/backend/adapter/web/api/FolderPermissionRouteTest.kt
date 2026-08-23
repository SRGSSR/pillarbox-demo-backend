package ch.srgssr.pillarbox.backend.adapter.web.api

import ch.srgssr.pillarbox.backend.adapter.web.api.dto.AddTeamMemberRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.AssignMediaRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderPermissionRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderPermissionResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.TeamRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.TeamResponseV1
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.test.mediaFixture
import ch.srgssr.pillarbox.backend.test.seedUser
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.toMediaRequestV1
import ch.srgssr.pillarbox.backend.test.tokenFor
import ch.srgssr.pillarbox.backend.test.tokenWithRoles
import ch.srgssr.pillarbox.backend.test.userFixture
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

private val adminRoles = setOf(Role.ADMIN)

class FolderPermissionRouteTest :
  ShouldSpec({
    should("manage folder grants as an editor with access") {
      testApplicationContext {
        val editor = tokenFor("user-1")
        seedUser(userFixture(oidcSub = "user-1"))
        val folder = client.createFolderV1("Folder", editor)

        val grant =
          client
            .grantV1(folder.id, editor, FolderPermissionRequestV1(oidcSub = "user-1"))
            .also { it shouldHaveStatus HttpStatusCode.Created }
            .body<FolderPermissionResponseV1>()

        grant.folderId shouldBe folder.id
        grant.oidcSub shouldBe "user-1"
        grant.canWrite shouldBe true

        val grants =
          client
            .get("/v1/folder/${folder.id}/permission") { bearerAuth(editor) }
            .body<List<FolderPermissionResponseV1>>()
        grants.map { it.id } shouldBe listOf(grant.id)

        client.delete("/v1/folder/${folder.id}/permission/${grant.id}") {
          bearerAuth(editor)
        } shouldHaveStatus HttpStatusCode.NoContent

        client
          .get("/v1/folder/${folder.id}/permission") { bearerAuth(editor) }
          .body<List<FolderPermissionResponseV1>>()
          .shouldBeEmpty()
      }
    }

    should("return BAD_REQUEST when not exactly one subject is provided") {
      testApplicationContext {
        val editor = tokenFor("user-1")
        seedUser(userFixture(oidcSub = "user-1"))
        val folder = client.createFolderV1("Folder", editor)

        client.grantV1(folder.id, editor, FolderPermissionRequestV1()) shouldHaveStatus
          HttpStatusCode.BadRequest
        client.grantV1(
          folder.id,
          editor,
          FolderPermissionRequestV1(oidcSub = "user-1", role = Role.WRITE),
        ) shouldHaveStatus HttpStatusCode.BadRequest
      }
    }

    should("return NOT_FOUND for grants on a non-existent folder") {
      testApplicationContext {
        val editor = tokenFor("user-1")
        seedUser(userFixture(oidcSub = "user-1"))

        client.get("/v1/folder/does-not-exist/permission") { bearerAuth(editor) } shouldHaveStatus
          HttpStatusCode.NotFound
        client.grantV1(
          "does-not-exist",
          editor,
          FolderPermissionRequestV1(oidcSub = "user-1"),
        ) shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return UNPROCESSABLE_ENTITY for unknown user or team subjects") {
      testApplicationContext {
        val editor = tokenFor("user-1")
        val folder = client.createFolderV1("Folder", editor)

        client.grantV1(folder.id, editor, FolderPermissionRequestV1(oidcSub = "ghost")) shouldHaveStatus
          HttpStatusCode.UnprocessableEntity
        client.grantV1(folder.id, editor, FolderPermissionRequestV1(teamId = "ghost-team")) shouldHaveStatus
          HttpStatusCode.UnprocessableEntity
      }
    }

    should("return NOT_FOUND when deleting a grant that belongs to another folder") {
      testApplicationContext {
        val editor = tokenFor("user-1")
        seedUser(userFixture(oidcSub = "user-1"))
        val folder = client.createFolderV1("Folder", editor)
        val other = client.createFolderV1("Other", editor)

        val grant =
          client
            .grantV1(folder.id, editor, FolderPermissionRequestV1(oidcSub = "user-1"))
            .body<FolderPermissionResponseV1>()

        client.delete("/v1/folder/${other.id}/permission/${grant.id}") {
          bearerAuth(editor)
        } shouldHaveStatus HttpStatusCode.NotFound
        client.delete("/v1/folder/${folder.id}/permission/does-not-exist") {
          bearerAuth(editor)
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("restrict folder mutations to granted users") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val granted = tokenFor("user-1")
        val stranger = tokenFor("user-2")
        seedUser(userFixture(oidcSub = "user-1"))

        val folder = client.createFolderV1("Restricted", stranger)
        client.grantV1(folder.id, admin, FolderPermissionRequestV1(oidcSub = "user-1")) shouldHaveStatus
          HttpStatusCode.Created

        client.renameFolderV1(folder.id, "Nope", stranger) shouldHaveStatus HttpStatusCode.Forbidden
        client.delete("/v1/folder/${folder.id}") { bearerAuth(stranger) } shouldHaveStatus
          HttpStatusCode.Forbidden
        client.post("/v1/folder") {
          bearerAuth(stranger)
          contentType(ContentType.Application.Json)
          setBody(FolderRequestV1(name = "Child", parentId = folder.id))
        } shouldHaveStatus HttpStatusCode.Forbidden
        client.get("/v1/folder/${folder.id}/permission") { bearerAuth(stranger) } shouldHaveStatus
          HttpStatusCode.Forbidden
        client.grantV1(folder.id, stranger, FolderPermissionRequestV1(oidcSub = "user-2")) shouldHaveStatus
          HttpStatusCode.Forbidden

        client.renameFolderV1(folder.id, "Renamed by granted", granted) shouldHaveStatus
          HttpStatusCode.Created
        client.renameFolderV1(folder.id, "Renamed by admin", admin) shouldHaveStatus HttpStatusCode.Created
      }
    }

    should("restrict media mutations to the folder grants") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val granted = tokenFor("user-1")
        val stranger = tokenFor("user-2")
        seedUser(userFixture(oidcSub = "user-1"))

        val folder = client.createFolderV1("Restricted", stranger)
        val open = client.createFolderV1("Open", stranger)

        val media = mediaFixture()
        client.post("/v1/media") {
          bearerAuth(stranger)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Created

        client.post("/v1/folder/${folder.id}/media") {
          bearerAuth(stranger)
          contentType(ContentType.Application.Json)
          setBody(AssignMediaRequestV1(mediaId = media.id))
        } shouldHaveStatus HttpStatusCode.Created

        client.grantV1(folder.id, admin, FolderPermissionRequestV1(oidcSub = "user-1")) shouldHaveStatus
          HttpStatusCode.Created

        // The stranger can no longer overwrite, delete, or move the media out.
        client.post("/v1/media") {
          bearerAuth(stranger)
          contentType(ContentType.Application.Json)
          setBody(media.toMediaRequestV1())
        } shouldHaveStatus HttpStatusCode.Forbidden
        client.delete("/v1/media/${media.id}") { bearerAuth(stranger) } shouldHaveStatus
          HttpStatusCode.Forbidden
        client.post("/v1/folder/${open.id}/media") {
          bearerAuth(stranger)
          contentType(ContentType.Application.Json)
          setBody(AssignMediaRequestV1(mediaId = media.id))
        } shouldHaveStatus HttpStatusCode.Forbidden

        client.delete("/v1/media/${media.id}") { bearerAuth(granted) } shouldHaveStatus
          HttpStatusCode.NoContent
      }
    }

    should("inherit grants from ancestor folders") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val granted = tokenFor("user-1")
        val stranger = tokenFor("user-2")
        seedUser(userFixture(oidcSub = "user-1"))

        val parent = client.createFolderV1("Parent", stranger)
        client.grantV1(parent.id, admin, FolderPermissionRequestV1(oidcSub = "user-1")) shouldHaveStatus
          HttpStatusCode.Created

        val child = client.createFolderV1("Child", granted, parentId = parent.id)

        client.renameFolderV1(child.id, "Nope", stranger, parentId = parent.id) shouldHaveStatus
          HttpStatusCode.Forbidden
        client.renameFolderV1(child.id, "Renamed", granted, parentId = parent.id) shouldHaveStatus
          HttpStatusCode.Created

        val grants =
          client
            .get("/v1/folder/${child.id}/permission") { bearerAuth(granted) }
            .body<List<FolderPermissionResponseV1>>()
        grants.map { it.folderId } shouldBe listOf(parent.id)
      }
    }

    should("re-open a restricted subtree with a role grant") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val granted = tokenFor("user-1")
        val stranger = tokenFor("user-2")
        seedUser(userFixture(oidcSub = "user-1"))

        val parent = client.createFolderV1("Parent", stranger)
        client.grantV1(parent.id, admin, FolderPermissionRequestV1(oidcSub = "user-1")) shouldHaveStatus
          HttpStatusCode.Created
        val child = client.createFolderV1("Child", granted, parentId = parent.id)

        client.renameFolderV1(child.id, "Nope", stranger, parentId = parent.id) shouldHaveStatus
          HttpStatusCode.Forbidden

        client.grantV1(child.id, granted, FolderPermissionRequestV1(role = Role.WRITE)) shouldHaveStatus
          HttpStatusCode.Created

        client.renameFolderV1(child.id, "Open again", stranger, parentId = parent.id) shouldHaveStatus
          HttpStatusCode.Created
        client.renameFolderV1(parent.id, "Still locked", stranger) shouldHaveStatus
          HttpStatusCode.Forbidden
      }
    }

    should("grant write access through team membership") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val member = tokenFor("user-3")
        val stranger = tokenFor("user-2")
        seedUser(userFixture(oidcSub = "user-3"))

        val team =
          client
            .post("/v1/team") {
              bearerAuth(admin)
              contentType(ContentType.Application.Json)
              setBody(TeamRequestV1(name = "Test Team"))
            }.body<TeamResponseV1>()
        client.post("/v1/team/${team.id}/member") {
          bearerAuth(admin)
          contentType(ContentType.Application.Json)
          setBody(AddTeamMemberRequestV1(oidcSub = "user-3"))
        } shouldHaveStatus HttpStatusCode.Created

        val folder = client.createFolderV1("Team Folder", admin)
        client.grantV1(folder.id, admin, FolderPermissionRequestV1(teamId = team.id)) shouldHaveStatus
          HttpStatusCode.Created

        client.renameFolderV1(folder.id, "Nope", stranger) shouldHaveStatus HttpStatusCode.Forbidden
        client.renameFolderV1(folder.id, "Renamed by member", member) shouldHaveStatus
          HttpStatusCode.Created
      }
    }

    should("never allow users without the WRITE role, even when granted") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val reader = tokenFor("reader", emptySet())
        seedUser(userFixture(oidcSub = "reader"))

        val folder = client.createFolderV1("Folder", admin)
        client.grantV1(folder.id, admin, FolderPermissionRequestV1(oidcSub = "reader")) shouldHaveStatus
          HttpStatusCode.Created

        client.renameFolderV1(folder.id, "Nope", reader) shouldHaveStatus HttpStatusCode.Forbidden
        client.get("/v1/folder/${folder.id}/permission") { bearerAuth(reader) } shouldHaveStatus
          HttpStatusCode.Forbidden
      }
    }

    should("return 401 on permission endpoints when no token is provided") {
      testApplicationContext {
        client.get("/v1/folder/any-id/permission") shouldHaveStatus HttpStatusCode.Unauthorized
        client.post("/v1/folder/any-id/permission") {
          contentType(ContentType.Application.Json)
        } shouldHaveStatus HttpStatusCode.Unauthorized
        client.delete("/v1/folder/any-id/permission/any-grant") shouldHaveStatus
          HttpStatusCode.Unauthorized
      }
    }
  })

private suspend fun HttpClient.createFolderV1(
  name: String,
  token: String,
  parentId: String? = null,
): FolderResponseV1 =
  post("/v1/folder") {
    bearerAuth(token)
    contentType(ContentType.Application.Json)
    setBody(FolderRequestV1(name = name, parentId = parentId))
  }.also { it shouldHaveStatus HttpStatusCode.Created }
    .body()

private suspend fun HttpClient.renameFolderV1(
  folderId: String,
  name: String,
  token: String,
  parentId: String? = null,
): HttpResponse =
  patch("/v1/folder/$folderId") {
    bearerAuth(token)
    contentType(ContentType.Application.Json)
    setBody(FolderRequestV1(name = name, parentId = parentId))
  }

private suspend fun HttpClient.grantV1(
  folderId: String,
  token: String,
  request: FolderPermissionRequestV1,
): HttpResponse =
  post("/v1/folder/$folderId/permission") {
    bearerAuth(token)
    contentType(ContentType.Application.Json)
    setBody(request)
  }
