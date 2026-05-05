package ch.srgssr.pillarbox.backend.auth

import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi

class UserInfoProviderTest :
  ShouldSpec({
    should("create a new user when no record exists for the OIDC subject") {
      val builder = UserInfoProviderBuilder()
      coEvery { builder.userRepository.findByOidcSub("test-sub") } returns null

      val result = builder.build().fetchAndSync("some-access-token")

      result.shouldNotBeNull()
      result.oidcSub shouldBe "test-sub"
      result.displayName shouldBe "Test User"
      coVerify { builder.userRepository.save(any()) }
    }

    should("update the display name when it has changed in the identity provider") {
      val builder = UserInfoProviderBuilder()
      val existing = User(oidcSub = "test-sub", displayName = "Old Name")
      coEvery { builder.userRepository.findByOidcSub("test-sub") } returns existing

      val result = builder.build().fetchAndSync("some-access-token")

      result.shouldNotBeNull()
      result.id shouldBe existing.id
      result.displayName shouldBe "Test User"
      coVerify { builder.userRepository.save(match { it.id == existing.id && it.displayName == result.displayName }) }
    }

    should("update lastLoginAt when updateLastLogin is true") {
      val builder = UserInfoProviderBuilder()
      val loginTime = Clock.System.now() - 1.hours
      val existing = User(oidcSub = "test-sub", displayName = "Test User", lastLoginAt = loginTime)
      coEvery { builder.userRepository.findByOidcSub("test-sub") } returns existing

      val result = builder.build().fetchAndSync("some-access-token", updateLastLogin = true)

      result.shouldNotBeNull()
      (result.lastLoginAt > loginTime) shouldBe true
    }

    should("preserve lastLoginAt when updateLastLogin is false") {
      val builder = UserInfoProviderBuilder()
      val loginTime = Clock.System.now() - 1.hours
      val existing = User(oidcSub = "test-sub", displayName = "Test User", lastLoginAt = loginTime)
      coEvery { builder.userRepository.findByOidcSub("test-sub") } returns existing

      val result = builder.build().fetchAndSync("some-access-token", updateLastLogin = false)

      result.shouldNotBeNull()
      result.lastLoginAt shouldBe loginTime
    }

    should("return null and skip the repository when the OIDC provider rejects the token") {
      val builder = UserInfoProviderBuilder().withStatus(HttpStatusCode.Unauthorized)

      val result = builder.build().fetchAndSync("expired-token")

      result.shouldBeNull()
      coVerify(exactly = 0) { builder.userRepository.findByOidcSub(any()) }
      coVerify(exactly = 0) { builder.userRepository.save(any()) }
    }

    should("return null when the OIDC UserInfo endpoint is unreachable") {
      val builder = UserInfoProviderBuilder().withNetworkFailure()

      val result = builder.build().fetchAndSync("some-access-token")

      result.shouldBeNull()
      coVerify(exactly = 0) { builder.userRepository.findByOidcSub(any()) }
      coVerify(exactly = 0) { builder.userRepository.save(any()) }
    }

    should("use preferred_username as display name when name is absent") {
      val builder = UserInfoProviderBuilder().withResponse("""{"sub":"test-sub","preferred_username":"jdoe"}""")
      coEvery { builder.userRepository.findByOidcSub("test-sub") } returns null

      val result = builder.build().fetchAndSync("some-access-token")

      result.shouldNotBeNull()
      result.displayName shouldBe "jdoe"
    }

    should("fall back to sub as display name when both name and preferred_username are absent") {
      val builder = UserInfoProviderBuilder().withResponse("""{"sub":"test-sub"}""")
      coEvery { builder.userRepository.findByOidcSub("test-sub") } returns null

      val result = builder.build().fetchAndSync("some-access-token")

      result.shouldNotBeNull()
      result.displayName shouldBe "test-sub"
    }
  })

@OptIn(ExperimentalUuidApi::class)
class UserInfoProviderBuilder {
  var userRepository = mockk<UserRepository>(relaxed = true)

  private var statusCode = HttpStatusCode.OK
  private var responseBody = """{"sub":"test-sub","name":"Test User"}"""
  private var networkFailure = false

  val engine =
    MockEngine { _ ->
      if (networkFailure) throw IOException("Connection refused")
      respond(
        if (statusCode == HttpStatusCode.OK) responseBody else "",
        statusCode,
        headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }

  fun withStatus(status: HttpStatusCode) = apply { statusCode = status }

  fun withResponse(body: String) = apply { responseBody = body }

  fun withNetworkFailure() = apply { networkFailure = true }

  fun build() =
    UserInfoProvider(
      userRepository = userRepository,
      httpClient =
        HttpClient(engine) {
          install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
          }
        },
      discovery =
        OpenIDDiscovery(
          authorizationEndpoint = "https://auth.example.com/auth",
          tokenEndpoint = "https://auth.example.com/token",
          jwksUri = "https://auth.example.com/jwks",
          userInfoEndpoint = "https://auth.example.com/userinfo",
          endSessionEndpoint = "https://auth.example.com/logout",
        ),
    )
}
