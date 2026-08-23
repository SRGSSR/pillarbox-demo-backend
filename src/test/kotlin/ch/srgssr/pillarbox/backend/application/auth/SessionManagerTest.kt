package ch.srgssr.pillarbox.backend.application.auth

import ch.srgssr.pillarbox.backend.application.auth.SessionManager
import ch.srgssr.pillarbox.backend.application.auth.UserManager
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.domain.model.SessionId
import ch.srgssr.pillarbox.backend.domain.port.IdentityProvider
import ch.srgssr.pillarbox.backend.domain.port.SessionCatalog
import ch.srgssr.pillarbox.backend.domain.port.TokenRefreshResponse
import ch.srgssr.pillarbox.backend.test.buildJwt
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SessionManagerTest :
  ShouldSpec({
    should("return session directly if not expired") {
      val builder = SessionManagerBuilder()
      val sessionId = SessionId()
      val session = SessionBuilder().build("stored-session-key")

      coEvery { builder.catalog.find(sessionId) } returns session

      val result = builder.build().validate(sessionId)

      result.shouldNotBeNull() shouldBeEqual session
      coVerify(exactly = 0) { builder.userManager.upsert(any()) }
      coVerify(exactly = 0) { builder.catalog.save(any()) }
    }

    should("return null immediately if session is not found in the catalog") {
      val builder = SessionManagerBuilder()

      coEvery { builder.catalog.find(any()) } returns null

      val result = builder.build().validate(SessionId("unknown-id"))

      result.shouldBeNull()
      coVerify(exactly = 0) { builder.userManager.upsert(any()) }
      coVerify(exactly = 0) { builder.catalog.save(any()) }
    }

    should("delete session and return null when expired without a refresh token") {
      val builder = SessionManagerBuilder()
      val sessionId = SessionId()
      val session = SessionBuilder().expired().build("stored-session-key")

      coEvery { builder.catalog.find(sessionId) } returns session

      val result = builder.build().validate(sessionId)

      result.shouldBeNull()
      coVerify(exactly = 0) { builder.userManager.upsert(any()) }
      coVerify { builder.catalog.delete(session.sessionId) }
    }

    should("refresh and return updated session when expired with a valid refresh token") {
      val refreshResponse =
        TokenRefreshResponse(
          accessToken = buildJwt(subject = "test-sub", name = "Test User"),
          refreshToken = "new-refresh-token",
          expiresIn = 3600L,
        )
      val builder = SessionManagerBuilder().withSuccessfulRefresh(refreshResponse)
      val sessionId = SessionId()
      val session = SessionBuilder().expired().withRefreshToken().build("stored-session-key")

      coEvery { builder.catalog.find(sessionId) } returns session

      val result = builder.build().validate(sessionId)

      result.shouldNotBeNull()
      result.accessToken shouldBe refreshResponse.accessToken
      result.refreshToken shouldBe refreshResponse.refreshToken
      (result.expiresAt > session.expiresAt) shouldBe true
      coVerify { builder.tokenProvider.refresh(session.refreshToken!!) }
      coVerify { builder.catalog.save(match { it.sessionId == session.sessionId }) }
    }

    should("delete session and return null when expired and token refresh fails") {
      val builder = SessionManagerBuilder().withFailedRefresh()
      val sessionId = SessionId()
      val session = SessionBuilder().expired().withRefreshToken().build("stored-session-key")

      coEvery { builder.catalog.find(sessionId) } returns session

      val result = builder.build().validate(sessionId)

      result.shouldBeNull()
      coVerify(exactly = 0) { builder.userManager.upsert(any()) }
      coVerify { builder.tokenProvider.refresh(session.refreshToken!!) }
      coVerify { builder.catalog.delete(session.sessionId) }
    }
  })

class SessionManagerBuilder {
  var catalog = mockk<SessionCatalog>(relaxed = true)
  var userManager = mockk<UserManager>(relaxed = true)
  var tokenProvider = mockk<IdentityProvider>(relaxed = true)

  fun withSuccessfulRefresh(response: TokenRefreshResponse) =
    apply {
      coEvery { tokenProvider.refresh(any()) } returns response
    }

  fun withFailedRefresh() =
    apply {
      coEvery { tokenProvider.refresh(any()) } returns null
    }

  fun build() =
    SessionManager(
      catalog = catalog,
      userManager = userManager,
      tokenProvider = tokenProvider,
    )
}

@OptIn(ExperimentalUuidApi::class)
class SessionBuilder {
  private var expiresAt: Instant = Clock.System.now() + 24.hours
  private var refreshToken: String? = null

  fun expired() =
    apply {
      this.expiresAt = Clock.System.now() - 1.seconds
    }

  fun withRefreshToken() =
    apply {
      this.refreshToken = "stub-refresh-token"
    }

  fun build(sessionId: String) =
    Session(
      sessionId = sessionId,
      accessToken = Uuid.random().toString(),
      refreshToken = refreshToken,
      expiresAt = expiresAt,
      oidcSub = "test-sub",
    )
}
