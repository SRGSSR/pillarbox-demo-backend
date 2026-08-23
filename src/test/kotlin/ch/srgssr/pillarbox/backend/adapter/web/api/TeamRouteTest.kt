package ch.srgssr.pillarbox.backend.adapter.web.api

import ch.srgssr.pillarbox.backend.adapter.web.api.dto.AddTeamMemberRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.TeamRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.TeamResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.UserResponseV1
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.test.seedUser
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.token
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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

private val adminRoles = setOf(Role.ADMIN)

class TeamRouteTest :
  ShouldSpec({
    should("return an empty list if no teams are stored") {
      testApplicationContext {
        val response = client.get("/v1/team") { bearerAuth(tokenWithRoles(adminRoles)) }

        response shouldHaveStatus HttpStatusCode.OK
        response.body<List<TeamResponseV1>>().shouldBeEmpty()
      }
    }

    should("create, get and delete a team") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val created = client.createTeamV1("Test Team", admin)

        created.name shouldBe "Test Team"

        client
          .get("/v1/team/${created.id}") { bearerAuth(admin) }
          .body<TeamResponseV1>()
          .name shouldBe "Test Team"

        client.delete("/v1/team/${created.id}") { bearerAuth(admin) } shouldHaveStatus HttpStatusCode.NoContent

        client.get("/v1/team/${created.id}") { bearerAuth(admin) } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return paginated teams correctly") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        repeat(20) { client.createTeamV1("Test Team $it", admin) }

        client
          .get("/v1/team?limit=5&offset=0") { bearerAuth(admin) }
          .body<List<TeamResponseV1>>()
          .size shouldBe 5
        client
          .get("/v1/team?limit=1&offset=20") { bearerAuth(admin) }
          .body<List<TeamResponseV1>>()
          .shouldBeEmpty()
      }
    }

    should("return NOT_FOUND when getting a non-existent team") {
      testApplicationContext {
        client.get("/v1/team/does-not-exist") {
          bearerAuth(tokenWithRoles(adminRoles))
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return NOT_FOUND when deleting a non-existent team") {
      testApplicationContext {
        client.delete("/v1/team/does-not-exist") {
          bearerAuth(tokenWithRoles(adminRoles))
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return NOT_FOUND when listing members of a non-existent team") {
      testApplicationContext {
        client.get("/v1/team/does-not-exist/member") {
          bearerAuth(tokenWithRoles(adminRoles))
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("add a member to a team and list it") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val team = client.createTeamV1("Test Team", admin)
        val user = seedUser(userFixture(oidcSub = "user-1"))

        client.post("/v1/team/${team.id}/member") {
          bearerAuth(admin)
          contentType(ContentType.Application.Json)
          setBody(AddTeamMemberRequestV1(oidcSub = user.oidcSub))
        } shouldHaveStatus HttpStatusCode.Created

        val members =
          client
            .get("/v1/team/${team.id}/member") { bearerAuth(admin) }
            .body<List<UserResponseV1>>()

        members.size shouldBe 1
        members.first().oidcSub shouldBe user.oidcSub
      }
    }

    should("not duplicate membership when adding the same member twice") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val team = client.createTeamV1("Test Team", admin)
        val user = seedUser(userFixture(oidcSub = "user-1"))

        repeat(2) {
          client.post("/v1/team/${team.id}/member") {
            bearerAuth(admin)
            contentType(ContentType.Application.Json)
            setBody(AddTeamMemberRequestV1(oidcSub = user.oidcSub))
          } shouldHaveStatus HttpStatusCode.Created
        }

        client
          .get("/v1/team/${team.id}/member") { bearerAuth(admin) }
          .body<List<UserResponseV1>>()
          .size shouldBe 1
      }
    }

    should("allow a user to be a member of several teams") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val user = seedUser(userFixture(oidcSub = "user-1"))

        listOf("Team A", "Team B").forEach { name ->
          val team = client.createTeamV1(name, admin)
          client.post("/v1/team/${team.id}/member") {
            bearerAuth(admin)
            contentType(ContentType.Application.Json)
            setBody(AddTeamMemberRequestV1(oidcSub = user.oidcSub))
          } shouldHaveStatus HttpStatusCode.Created
        }
      }
    }

    should("remove a member from a team") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val team = client.createTeamV1("Test Team", admin)
        val user = seedUser(userFixture(oidcSub = "user-1"))

        client.post("/v1/team/${team.id}/member") {
          bearerAuth(admin)
          contentType(ContentType.Application.Json)
          setBody(AddTeamMemberRequestV1(oidcSub = user.oidcSub))
        } shouldHaveStatus HttpStatusCode.Created

        client.delete("/v1/team/${team.id}/member/${user.oidcSub}") {
          bearerAuth(admin)
        } shouldHaveStatus HttpStatusCode.NoContent

        client
          .get("/v1/team/${team.id}/member") { bearerAuth(admin) }
          .body<List<UserResponseV1>>()
          .shouldBeEmpty()

        client.delete("/v1/team/${team.id}/member/${user.oidcSub}") {
          bearerAuth(admin)
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return NOT_FOUND when adding a member to a non-existent team") {
      testApplicationContext {
        val user = seedUser(userFixture(oidcSub = "user-1"))

        client.post("/v1/team/does-not-exist/member") {
          bearerAuth(tokenWithRoles(adminRoles))
          contentType(ContentType.Application.Json)
          setBody(AddTeamMemberRequestV1(oidcSub = user.oidcSub))
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return UNPROCESSABLE_ENTITY when adding a non-existent user to a team") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val team = client.createTeamV1("Test Team", admin)

        client.post("/v1/team/${team.id}/member") {
          bearerAuth(admin)
          contentType(ContentType.Application.Json)
          setBody(AddTeamMemberRequestV1(oidcSub = "does-not-exist"))
        } shouldHaveStatus HttpStatusCode.UnprocessableEntity
      }
    }

    should("keep the user when deleting a team with members") {
      testApplicationContext {
        val admin = tokenWithRoles(adminRoles)
        val team = client.createTeamV1("Test Team", admin)
        val user = seedUser(userFixture(oidcSub = "user-1"))

        client.post("/v1/team/${team.id}/member") {
          bearerAuth(admin)
          contentType(ContentType.Application.Json)
          setBody(AddTeamMemberRequestV1(oidcSub = user.oidcSub))
        } shouldHaveStatus HttpStatusCode.Created

        client.delete("/v1/team/${team.id}") { bearerAuth(admin) } shouldHaveStatus HttpStatusCode.NoContent

        client.get("/v1/user/${user.oidcSub}") { bearerAuth(admin) } shouldHaveStatus HttpStatusCode.OK
      }
    }

    should("return 401 on all endpoints when no token is provided") {
      testApplicationContext {
        client.get("/v1/team") shouldHaveStatus HttpStatusCode.Unauthorized
        client.get("/v1/team/any-id") shouldHaveStatus HttpStatusCode.Unauthorized
        client.get("/v1/team/any-id/member") shouldHaveStatus HttpStatusCode.Unauthorized
        client.post("/v1/team") { contentType(ContentType.Application.Json) } shouldHaveStatus
          HttpStatusCode.Unauthorized
        client.delete("/v1/team/any-id") shouldHaveStatus HttpStatusCode.Unauthorized
        client.post("/v1/team/any-id/member") { contentType(ContentType.Application.Json) } shouldHaveStatus
          HttpStatusCode.Unauthorized
        client.delete("/v1/team/any-id/member/any-user") shouldHaveStatus HttpStatusCode.Unauthorized
      }
    }

    should("allow editors to list teams and members but not to modify them") {
      testApplicationContext {
        val team = client.createTeamV1("Test Team", tokenWithRoles(adminRoles))

        client.get("/v1/team") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.OK
        client.get("/v1/team/${team.id}") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.OK
        client.get("/v1/team/${team.id}/member") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.OK
        client.post("/v1/team") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(TeamRequestV1(name = "Another Team"))
        } shouldHaveStatus HttpStatusCode.Forbidden
        client.delete("/v1/team/${team.id}") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.Forbidden
        client.post("/v1/team/${team.id}/member") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(AddTeamMemberRequestV1(oidcSub = "any-user"))
        } shouldHaveStatus HttpStatusCode.Forbidden
        client.delete("/v1/team/${team.id}/member/any-user") { bearerAuth(token) } shouldHaveStatus
          HttpStatusCode.Forbidden
      }
    }

    should("return 403 on all endpoints when authenticated without roles") {
      testApplicationContext {
        val readerToken = tokenWithRoles(emptySet())

        client.get("/v1/team") { bearerAuth(readerToken) } shouldHaveStatus HttpStatusCode.Forbidden
        client.get("/v1/team/any-id") { bearerAuth(readerToken) } shouldHaveStatus HttpStatusCode.Forbidden
        client.get("/v1/team/any-id/member") { bearerAuth(readerToken) } shouldHaveStatus HttpStatusCode.Forbidden
        client.post("/v1/team") {
          bearerAuth(readerToken)
          contentType(ContentType.Application.Json)
          setBody(TeamRequestV1(name = "Test Team"))
        } shouldHaveStatus HttpStatusCode.Forbidden
        client.delete("/v1/team/any-id") { bearerAuth(readerToken) } shouldHaveStatus HttpStatusCode.Forbidden
      }
    }
  })

private suspend fun HttpClient.createTeamV1(
  name: String,
  token: String,
): TeamResponseV1 =
  post("/v1/team") {
    bearerAuth(token)
    contentType(ContentType.Application.Json)
    setBody(TeamRequestV1(name = name))
  }.also { it shouldHaveStatus HttpStatusCode.Created }
    .body()
