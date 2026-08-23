package ch.srgssr.pillarbox.backend.adapter.web.console

import ch.srgssr.pillarbox.backend.adapter.web.api.Navigation
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderResponseV1
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.test.count
import ch.srgssr.pillarbox.backend.test.hxDelete
import ch.srgssr.pillarbox.backend.test.hxGet
import ch.srgssr.pillarbox.backend.test.hxPatch
import ch.srgssr.pillarbox.backend.test.hxPost
import ch.srgssr.pillarbox.backend.test.login
import ch.srgssr.pillarbox.backend.test.seedTeam
import ch.srgssr.pillarbox.backend.test.seedUser
import ch.srgssr.pillarbox.backend.test.teamFixture
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.userFixture
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import org.jsoup.Jsoup

class ConsolePermissionDialogTest :
  ShouldSpec({
    should("render a dialog skeleton that lazy-loads the permission list") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        val folder = client.createFolderV1("Shows")

        val doc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/folder-permissions?folderId=${folder.id}").bodyAsText(),
          )

        doc.count("#permission-list[hx-get*=folder-permissions-list]") shouldBe 1
        doc.count("[data-subject-search]") shouldBe 1
      }
    }

    should("render the permission list with the provisioned role rows") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        val folder = client.createFolderV1("Shows")

        val doc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/folder-permissions-list?folderId=${folder.id}").bodyAsText(),
          )

        doc.text() shouldContain "Admin"
        doc.text() shouldContain "Editor"
        doc.text() shouldContain "Viewer"
        doc.count("select[hx-patch\$=/permission/editor]") shouldBe 1
      }
    }

    should("list selectable subjects as labelled datalist options") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedUser(userFixture(oidcSub = "grantee", displayName = "Grace Grantee"))

        val response = client.hxGet("${Navigation.CONSOLE}/fragments/subject-options?subject=grace")
        val doc = Jsoup.parse(response.bodyAsText())

        doc.count("option") shouldBe 1
        doc.select("option").attr("value") shouldBe "Grace Grantee"
        doc.select("option").attr("label") shouldBe "User"
        doc.select("option").attr("data-id") shouldBe "user:grantee"
      }
    }

    should("add a grant returning its row, then revoke it") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedUser(userFixture(oidcSub = "grantee", displayName = "Grace Grantee"))
        val folder = client.createFolderV1("Shows")

        val added =
          client.hxPost("${Navigation.CONSOLE}/actions/folder/${folder.id}/permission") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("subjectRef" to "user:grantee", "level" to "write").formUrlEncode())
          }
        added shouldHaveStatus HttpStatusCode.OK
        val addedBody = added.bodyAsText()
        addedBody shouldContain "Grace Grantee"

        val deleteUrl = Jsoup.parse(addedBody).select(".permission-delete").attr("hx-delete")
        deleteUrl shouldContain "/permission/user:grantee"

        val revoked = client.hxDelete(deleteUrl)
        revoked shouldHaveStatus HttpStatusCode.NoContent

        val list = client.hxGet("${Navigation.CONSOLE}/fragments/folder-permissions-list?folderId=${folder.id}")
        list.bodyAsText() shouldNotContain "Grace Grantee"
      }
    }

    should("show grants inherited from an ancestor folder as locked") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedUser(userFixture(oidcSub = "grantee", displayName = "Grace Grantee"))
        val parent = client.createFolderV1("Parent")
        val child = client.createFolderV1("Child", parentId = parent.id)

        client.hxPost("${Navigation.CONSOLE}/actions/folder/${parent.id}/permission") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody(listOf("subjectRef" to "user:grantee", "level" to "write").formUrlEncode())
        } shouldHaveStatus HttpStatusCode.OK

        val doc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/folder-permissions-list?folderId=${child.id}").bodyAsText(),
          )

        doc.text() shouldContain "Grace Grantee"
        doc.count(".permission-delete") shouldBe 0
      }
    }

    should("restrict editors to view through the editor toggle") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        val folder = client.createFolderV1("Shows")

        val response =
          client.hxPatch("${Navigation.CONSOLE}/actions/folder/${folder.id}/permission/editor") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("level" to "view").formUrlEncode())
          }
        response shouldHaveStatus HttpStatusCode.NoContent

        val doc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/folder-permissions-list?folderId=${folder.id}").bodyAsText(),
          )
        doc.count("select[hx-patch\$=/permission/editor] option[value=view][selected]") shouldBe 1
      }
    }

    should("reflect an editor restriction inherited from an ancestor") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        val parent = client.createFolderV1("Parent")
        val child = client.createFolderV1("Child", parentId = parent.id)

        client.hxPatch("${Navigation.CONSOLE}/actions/folder/${parent.id}/permission/editor") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody(listOf("level" to "view").formUrlEncode())
        } shouldHaveStatus HttpStatusCode.NoContent

        val doc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/folder-permissions-list?folderId=${child.id}").bodyAsText(),
          )
        doc.count("select[hx-patch\$=/permission/editor] option[value=view][selected]") shouldBe 1
      }
    }

    should("show a subject granted on both the folder and an ancestor only once, as editable") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedUser(userFixture(oidcSub = "grantee", displayName = "Grace Grantee"))
        val parent = client.createFolderV1("Parent")
        val child = client.createFolderV1("Child", parentId = parent.id)

        listOf(parent.id, child.id).forEach { folderId ->
          client.hxPost("${Navigation.CONSOLE}/actions/folder/$folderId/permission") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("subjectRef" to "user:grantee", "level" to "write").formUrlEncode())
          } shouldHaveStatus HttpStatusCode.OK
        }

        val doc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/folder-permissions-list?folderId=${child.id}").bodyAsText(),
          )

        doc.select(".permission-name").count { it.text() == "Grace Grantee" } shouldBe 1
        doc.count("[hx-delete\$=/permission/user:grantee]") shouldBe 1
      }
    }

    should("update an existing grant in place when its subject is granted again") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedUser(userFixture(oidcSub = "grantee", displayName = "Grace Grantee"))
        val folder = client.createFolderV1("Shows")

        listOf("write", "view").forEach { level ->
          client.hxPost("${Navigation.CONSOLE}/actions/folder/${folder.id}/permission") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("subjectRef" to "user:grantee", "level" to level).formUrlEncode())
          } shouldHaveStatus HttpStatusCode.OK
        }

        val doc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/folder-permissions-list?folderId=${folder.id}").bodyAsText(),
          )

        doc.count("[hx-delete\$=/permission/user:grantee]") shouldBe 1
        doc.count("select[hx-patch\$=/permission/user:grantee] option[value=view][selected]") shouldBe 1
      }
    }

    should("swap an existing grant row in place instead of appending a duplicate when re-granting its subject") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedUser(userFixture(oidcSub = "grantee", displayName = "Grace Grantee"))
        val folder = client.createFolderV1("Shows")

        val firstBody =
          client
            .hxPost("${Navigation.CONSOLE}/actions/folder/${folder.id}/permission") {
              contentType(ContentType.Application.FormUrlEncoded)
              setBody(listOf("subjectRef" to "user:grantee", "level" to "write").formUrlEncode())
            }.bodyAsText()
        // A new subject is appended to the list, so its row must not be an out-of-band swap.
        Jsoup.parse(firstBody).count("[hx-swap-oob]") shouldBe 0

        val secondBody =
          client
            .hxPost("${Navigation.CONSOLE}/actions/folder/${folder.id}/permission") {
              contentType(ContentType.Application.FormUrlEncoded)
              setBody(listOf("subjectRef" to "user:grantee", "level" to "view").formUrlEncode())
            }.bodyAsText()
        // The subject already has a row, so it is swapped in place rather than appended again.
        Jsoup.parse(secondBody).count("#permission-grant-user-grantee[hx-swap-oob]") shouldBe 1
      }
    }

    should("reveal the inherited grant as locked when its overriding own grant is revoked") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedUser(userFixture(oidcSub = "grantee", displayName = "Grace Grantee"))
        val parent = client.createFolderV1("Parent")
        val child = client.createFolderV1("Child", parentId = parent.id)

        // Grant on the parent (inherited by the child), then override it on the child itself.
        listOf(parent.id to "write", child.id to "view").forEach { (folderId, level) ->
          client.hxPost("${Navigation.CONSOLE}/actions/folder/$folderId/permission") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("subjectRef" to "user:grantee", "level" to level).formUrlEncode())
          } shouldHaveStatus HttpStatusCode.OK
        }

        val revoked = client.hxDelete("${Navigation.CONSOLE}/actions/folder/${child.id}/permission/user:grantee")

        revoked shouldHaveStatus HttpStatusCode.OK
        // The row is replaced in place rather than deleted, since the ancestor's grant still applies.
        revoked.headers["HX-Reswap"] shouldBe "outerHTML"
        val doc = Jsoup.parse(revoked.bodyAsText())
        doc.count("#permission-grant-user-grantee") shouldBe 1
        doc.count(".permission-lock") shouldBe 1
        doc.count(".permission-delete") shouldBe 0
      }
    }

    should("swap an inherited grant row in place when the subject is granted on the folder itself") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedUser(userFixture(oidcSub = "grantee", displayName = "Grace Grantee"))
        val parent = client.createFolderV1("Parent")
        val child = client.createFolderV1("Child", parentId = parent.id)

        client.hxPost("${Navigation.CONSOLE}/actions/folder/${parent.id}/permission") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody(listOf("subjectRef" to "user:grantee", "level" to "write").formUrlEncode())
        } shouldHaveStatus HttpStatusCode.OK

        val body =
          client
            .hxPost("${Navigation.CONSOLE}/actions/folder/${child.id}/permission") {
              contentType(ContentType.Application.FormUrlEncoded)
              setBody(listOf("subjectRef" to "user:grantee", "level" to "view").formUrlEncode())
            }.bodyAsText()

        // The child already shows the subject as an inherited (locked) row, so the new own grant
        // replaces it in place rather than appending a duplicate.
        Jsoup.parse(body).count("#permission-grant-user-grantee[hx-swap-oob]") shouldBe 1
      }
    }

    should("grant a team and list it as a team subject") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedTeam(teamFixture(id = "team-news", name = "Newsroom"))
        val folder = client.createFolderV1("Shows")

        val options =
          Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/subject-options?subject=news").bodyAsText())
        options.count("option") shouldBe 1
        options.select("option").attr("data-id") shouldBe "team:team-news"

        val added =
          client.hxPost("${Navigation.CONSOLE}/actions/folder/${folder.id}/permission") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("subjectRef" to "team:team-news", "level" to "view").formUrlEncode())
          }
        added shouldHaveStatus HttpStatusCode.OK
        added.bodyAsText() shouldContain "Newsroom"

        val doc =
          Jsoup.parse(
            client.hxGet("${Navigation.CONSOLE}/fragments/folder-permissions-list?folderId=${folder.id}").bodyAsText(),
          )
        doc.count("[hx-delete\$=/permission/team:team-news]") shouldBe 1
      }
    }

    should("reject a grant whose subject is unknown") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        val folder = client.createFolderV1("Shows")

        client.hxPost("${Navigation.CONSOLE}/actions/folder/${folder.id}/permission") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody(listOf("subjectRef" to "user:ghost", "level" to "write").formUrlEncode())
        } shouldHaveStatus HttpStatusCode.UnprocessableEntity
      }
    }

    should("return not found when revoking a grant that does not exist") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        val folder = client.createFolderV1("Shows")

        client.hxDelete(
          "${Navigation.CONSOLE}/actions/folder/${folder.id}/permission/user:ghost",
        ) shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("forbid managing the permissions of a folder the editor cannot write") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))
        seedUser(userFixture(oidcSub = "other", displayName = "Olive Other"))
        val folder = client.createFolderV1("Shows")

        // Restricting the folder to someone else locks the editor out of every subsequent request.
        client.hxPost("${Navigation.CONSOLE}/actions/folder/${folder.id}/permission") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody(listOf("subjectRef" to "user:other", "level" to "write").formUrlEncode())
        } shouldHaveStatus HttpStatusCode.OK

        client.hxGet(
          "${Navigation.CONSOLE}/fragments/folder-permissions?folderId=${folder.id}",
        ) shouldHaveStatus HttpStatusCode.Forbidden
      }
    }
  })

private suspend fun HttpClient.createFolderV1(
  name: String,
  parentId: String? = null,
): FolderResponseV1 =
  post("/v1/folder") {
    contentType(ContentType.Application.Json)
    setBody(FolderRequestV1(name = name, parentId = parentId))
  }.also { it shouldHaveStatus HttpStatusCode.Created }
    .body()
