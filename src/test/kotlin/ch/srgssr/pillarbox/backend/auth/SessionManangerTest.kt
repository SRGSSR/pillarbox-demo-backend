package ch.srgssr.pillarbox.backend.auth

import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
import ch.srgssr.pillarbox.backend.test.buildJwt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
      val session = SessionBuilder().build()

      coEvery { builder.repository.find(session.sessionId) } returns session

      val result = builder.build().validate(SessionId(session.sessionId))

      result.shouldNotBeNull() shouldBeEqual session
      coVerify(exactly = 0) { builder.userManager.upsert(any()) }
      coVerify(exactly = 0) { builder.repository.save(any()) }
    }

    should("return null immediately if session is not found in repository") {
      val builder = SessionManagerBuilder()
      val sessionId = "unknown-id"

      coEvery { builder.repository.find(sessionId) } returns null

      val result = builder.build().validate(SessionId(sessionId))

      result.shouldBeNull()
      coVerify(exactly = 0) { builder.userManager.upsert(any()) }
      coVerify(exactly = 0) { builder.repository.save(any()) }
    }

    should("delete session and return null when expired without a refresh token") {
      val builder = SessionManagerBuilder()
      val session = SessionBuilder().expired().build()

      coEvery { builder.repository.find(session.sessionId) } returns session

      val result = builder.build().validate(SessionId(session.sessionId))

      result.shouldBeNull()
      coVerify(exactly = 0) { builder.userManager.upsert(any()) }
      coVerify { builder.repository.delete(session.sessionId) }
    }

    should("refresh and return updated session when expired with a valid refresh token") {
      val refreshResponse =
        TokenRefreshResponse(
          accessToken = buildJwt(subject = "test-sub", name = "Test User"),
          refreshToken = "new-refresh-token",
          expiresIn = 3600L,
        )
      val builder = SessionManagerBuilder().withSuccessfulRefresh(refreshResponse)
      val session = SessionBuilder().expired().withRefreshToken().build()

      coEvery { builder.repository.find(session.sessionId) } returns session

      val result = builder.build().validate(SessionId(session.sessionId))

      result.shouldNotBeNull()
      result.accessToken shouldBe refreshResponse.accessToken
      result.refreshToken shouldBe refreshResponse.refreshToken
      (result.expiresAt > session.expiresAt) shouldBe true
      coVerify { builder.tokenProvider.refresh(session.refreshToken!!) }
      coVerify { builder.repository.save(match { it.sessionId == session.sessionId }) }
    }

    should("delete session and return null when expired and token refresh fails") {
      val builder = SessionManagerBuilder().withFailedRefresh()
      val session = SessionBuilder().expired().withRefreshToken().build()

      coEvery { builder.repository.find(session.sessionId) } returns session

      val result = builder.build().validate(SessionId(session.sessionId))

      result.shouldBeNull()
      coVerify(exactly = 0) { builder.userManager.upsert(any()) }
      coVerify { builder.tokenProvider.refresh(session.refreshToken!!) }
      coVerify { builder.repository.delete(session.sessionId) }
    }
  })

class SessionManagerBuilder {
  var repository = mockk<SessionRepository>(relaxed = true)
  var userManager = mockk<UserManager>(relaxed = true)
  var tokenProvider = mockk<TokenProvider>(relaxed = true)

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
      repository = repository,
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

  fun build() =
    Session(
      accessToken = Uuid.random().toString(),
      refreshToken = refreshToken,
      expiresAt = expiresAt,
      oidcSub = "test-sub",
    )
}
