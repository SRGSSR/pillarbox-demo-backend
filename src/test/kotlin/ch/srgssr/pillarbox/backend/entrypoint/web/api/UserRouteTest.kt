package ch.srgssr.pillarbox.backend.entrypoint.web.api

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.SessionResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.UserResponseV1
import ch.srgssr.pillarbox.backend.test.seedSession
import ch.srgssr.pillarbox.backend.test.seedUser
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.token
import ch.srgssr.pillarbox.backend.test.tokenWithRoles
import ch.srgssr.pillarbox.backend.test.userFixture
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

private val adminRoles = setOf(Role.ADMIN)

class UserRouteTest :
  ShouldSpec({
    should("return an empty list if no users are stored") {
      testApplicationContext {
        val response = client.get("/v1/user") { bearerAuth(tokenWithRoles(adminRoles)) }

        response shouldHaveStatus HttpStatusCode.OK
        response.body<List<UserResponseV1>>().shouldBeEmpty()
      }
    }

    should("list stored users") {
      testApplicationContext {
        seedUser(userFixture(oidcSub = "user-1", displayName = "User One"))
        seedUser(userFixture(oidcSub = "user-2", displayName = "User Two", roles = setOf(Role.WRITE)))

        val users =
          client
            .get("/v1/user") { bearerAuth(tokenWithRoles(adminRoles)) }
            .body<List<UserResponseV1>>()

        users.size shouldBe 2
        users.map { it.oidcSub }.toSet() shouldBe setOf("user-1", "user-2")
      }
    }

    should("return paginated users correctly") {
      testApplicationContext {
        repeat(20) { seedUser(userFixture(oidcSub = "user-$it")) }
        val admin = tokenWithRoles(adminRoles)

        client
          .get("/v1/user?limit=5&offset=0") { bearerAuth(admin) }
          .body<List<UserResponseV1>>()
          .size shouldBe 5
        client
          .get("/v1/user?limit=1&offset=20") { bearerAuth(admin) }
          .body<List<UserResponseV1>>()
          .shouldBeEmpty()
      }
    }

    should("return a user by id") {
      testApplicationContext {
        val user = seedUser(userFixture(oidcSub = "user-1", displayName = "User One"))

        val response =
          client
            .get("/v1/user/${user.oidcSub}") { bearerAuth(tokenWithRoles(adminRoles)) }
            .body<UserResponseV1>()

        response.oidcSub shouldBe user.oidcSub
        response.displayName shouldBe user.displayName
      }
    }

    should("return NOT_FOUND when getting a non-existent user") {
      testApplicationContext {
        client.get("/v1/user/does-not-exist") {
          bearerAuth(tokenWithRoles(adminRoles))
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("list only the active sessions of a user") {
      testApplicationContext {
        val user = seedUser(userFixture(oidcSub = "user-1"))
        val other = seedUser(userFixture(oidcSub = "user-2"))

        val active =
          seedSession(
            Session(
              sessionId = "stored-session-active",
              accessToken = "active-access-token",
              oidcSub = user.oidcSub,
              expiresAt = Clock.System.now() + 1.hours,
            ),
          )
        seedSession(
          Session(
            sessionId = "stored-session-expired",
            accessToken = "expired-access-token",
            oidcSub = user.oidcSub,
            expiresAt = Clock.System.now() - 1.hours,
          ),
        )
        seedSession(
          Session(
            sessionId = "stored-session-other",
            accessToken = "other-access-token",
            oidcSub = other.oidcSub,
            expiresAt = Clock.System.now() + 1.hours,
          ),
        )

        val sessions =
          client
            .get("/v1/user/${user.oidcSub}/session") { bearerAuth(tokenWithRoles(adminRoles)) }
            .body<List<SessionResponseV1>>()

        sessions.size shouldBe 1
        sessions.first().publicId shouldBe active.publicId
        sessions.first().oidcSub shouldBe user.oidcSub
        sessions.first().expiresAt shouldBe active.expiresAt
      }
    }

    should("list sessions most recently updated first") {
      testApplicationContext {
        val user = seedUser(userFixture(oidcSub = "user-1"))
        val seeded =
          listOf(2.hours, 1.hours, 3.hours).map { lifetime ->
            seedSession(
              Session(
                sessionId = "stored-session-$lifetime",
                accessToken = "access-token",
                oidcSub = user.oidcSub,
                expiresAt = Clock.System.now() + lifetime,
              ),
            )
          }

        val sessions =
          client
            .get("/v1/user/${user.oidcSub}/session") { bearerAuth(tokenWithRoles(adminRoles)) }
            .body<List<SessionResponseV1>>()

        sessions.map { it.publicId } shouldBe
          seeded.sortedByDescending { it.expiresAt }.map { it.publicId }
      }
    }

    should("not expose token material or full session ids") {
      testApplicationContext {
        val user = seedUser(userFixture(oidcSub = "user-1"))
        val session =
          seedSession(
            Session(
              sessionId = "stored-session-secret",
              accessToken = "secret-access-token",
              refreshToken = "secret-refresh-token",
              idToken = "secret-id-token",
              oidcSub = user.oidcSub,
              expiresAt = Clock.System.now() + 1.hours,
            ),
          )

        val body =
          client
            .get("/v1/user/${user.oidcSub}/session") { bearerAuth(tokenWithRoles(adminRoles)) }
            .bodyAsText()

        body shouldNotContain "secret-access-token"
        body shouldNotContain "secret-refresh-token"
        body shouldNotContain "secret-id-token"
        body shouldNotContain session.sessionId
        body shouldContain session.publicId
      }
    }

    should("return NOT_FOUND when listing sessions of a non-existent user") {
      testApplicationContext {
        client.get("/v1/user/does-not-exist/session") {
          bearerAuth(tokenWithRoles(adminRoles))
        } shouldHaveStatus HttpStatusCode.NotFound
      }
    }

    should("return 401 on all endpoints when no token is provided") {
      testApplicationContext {
        client.get("/v1/user") shouldHaveStatus HttpStatusCode.Unauthorized
        client.get("/v1/user/any-id") shouldHaveStatus HttpStatusCode.Unauthorized
        client.get("/v1/user/any-id/session") shouldHaveStatus HttpStatusCode.Unauthorized
      }
    }

    should("return 403 on all endpoints when authenticated without the ADMIN role") {
      testApplicationContext {
        client.get("/v1/user") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.Forbidden
        client.get("/v1/user/any-id") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.Forbidden
        client.get("/v1/user/any-id/session") { bearerAuth(token) } shouldHaveStatus HttpStatusCode.Forbidden
      }
    }
  })
