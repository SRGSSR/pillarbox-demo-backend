package ch.srgssr.pillarbox.backend.auth

import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
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
    should("return session from cache and SKIP network call if fresh") {
      val builder = SessionManagerBuilder()
      val session = SessionBuilder().lastCheckedInsideInterval(builder.interval).build()

      coEvery { builder.repository.find(session.sessionId) } returns session

      val result = builder.build().validate(SessionId(session.sessionId))

      result.shouldNotBeNull() shouldBeEqual session
      coVerify(exactly = 0) { builder.userInfoProvider.fetchAndSync(any()) }
      coVerify(exactly = 0) { builder.repository.save(any()) }
    }

    should("return null immediately and skip network if session is not found in repository") {
      val builder = SessionManagerBuilder()
      val sessionId = "unknown-id"

      coEvery { builder.repository.find(sessionId) } returns null

      val result = builder.build().validate(SessionId(sessionId))

      result.shouldBeNull()
      coVerify(exactly = 0) { builder.userInfoProvider.fetchAndSync(any()) }
      coVerify(exactly = 0) { builder.repository.save(any()) }
    }

    should("perform network validation and update cache if session is stale") {
      val builder = SessionManagerBuilder().withValidToken()
      val session = SessionBuilder().lastCheckedOutsideInterval(builder.interval).build()

      coEvery { builder.repository.find(session.sessionId) } returns session

      val result = builder.build().validate(SessionId(session.sessionId))

      result.shouldNotBeNull()
      (result.lastChecked > session.lastChecked) shouldBe true
      coVerify { builder.userInfoProvider.fetchAndSync(session.accessToken) }
      coVerify { builder.repository.save(match { it.sessionId == session.sessionId }) }
    }

    should("delete session and return null if the OIDC provider revokes the token") {
      val builder = SessionManagerBuilder().withInvalidToken()
      val session = SessionBuilder().lastCheckedOutsideInterval(builder.interval).build()

      coEvery { builder.repository.find(session.sessionId) } returns session

      val result = builder.build().validate(SessionId(session.sessionId))

      result.shouldBeNull()
      coVerify { builder.userInfoProvider.fetchAndSync(session.accessToken) }
      coVerify { builder.repository.delete(session.sessionId) }
    }

    should("invalidate when session has expired even if it is inside validation interval") {
      val builder = SessionManagerBuilder()
      val session = SessionBuilder().lastCheckedInsideInterval(builder.interval).expired().build()

      coEvery { builder.repository.find(session.sessionId) } returns session

      val result = builder.build().validate(SessionId(session.sessionId))

      result.shouldBeNull()
      coVerify(exactly = 0) { builder.userInfoProvider.fetchAndSync(any()) }
      coVerify { builder.repository.delete(session.sessionId) }
    }

    should("invalidate when session has expired and is outside validation interval") {
      val builder = SessionManagerBuilder()
      val session = SessionBuilder().lastCheckedOutsideInterval(builder.interval).expired().build()

      coEvery { builder.repository.find(session.sessionId) } returns session

      val result = builder.build().validate(SessionId(session.sessionId))

      result.shouldBeNull()
      coVerify(exactly = 0) { builder.userInfoProvider.fetchAndSync(any()) }
      coVerify { builder.repository.delete(session.sessionId) }
    }
  })

class SessionManagerBuilder {
  var repository = mockk<SessionRepository>(relaxed = true)
  var userInfoProvider = mockk<UserInfoProvider>(relaxed = true)
  var interval = 60L

  private val stubUser =
    User(
      oidcSub = "test-sub",
      displayName = "Test User",
    )

  fun withValidToken() =
    apply {
      coEvery { userInfoProvider.fetchAndSync(any()) } returns stubUser
    }

  fun withInvalidToken() =
    apply {
      coEvery { userInfoProvider.fetchAndSync(any()) } returns null
    }

  fun build() =
    SessionManager(
      repository = repository,
      userInfoProvider = userInfoProvider,
      validationIntervalSeconds = interval,
    )
}

@OptIn(ExperimentalUuidApi::class)
class SessionBuilder {
  private var lastChecked: Instant = Clock.System.now()
  private var expiresAt: Instant = lastChecked + 24.hours

  fun lastCheckedOutsideInterval(intervalSeconds: Long) =
    apply {
      require(intervalSeconds > 0) { "Interval must be > 0, but was $intervalSeconds" }
      this.lastChecked = Clock.System.now() - (intervalSeconds + 10).seconds
    }

  fun lastCheckedInsideInterval(intervalSeconds: Long) =
    apply {
      require(intervalSeconds > 5) {
        "Interval must be > 5 seconds to calculate a 'fresh' timestamp, but was $intervalSeconds"
      }
      this.lastChecked = Clock.System.now() - (intervalSeconds - 5).seconds
    }

  fun expired() =
    apply {
      this.expiresAt = Clock.System.now() - 1.seconds
    }

  fun build() =
    Session(
      accessToken = Uuid.random().toString(),
      lastChecked = lastChecked,
      expiresAt = expiresAt,
    )
}
