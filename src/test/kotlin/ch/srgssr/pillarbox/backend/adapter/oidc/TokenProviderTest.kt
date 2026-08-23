package ch.srgssr.pillarbox.backend.adapter.oidc

import ch.srgssr.pillarbox.backend.adapter.oidc.TokenProvider
import ch.srgssr.pillarbox.backend.application.auth.AuthConfig
import ch.srgssr.pillarbox.backend.test.mockOAuth2Server
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback

class TokenProviderTest :
  ShouldSpec({
    lateinit var mockServer: MockOAuth2Server
    lateinit var tokenProvider: TokenProvider

    beforeTest {
      mockServer = mockOAuth2Server().also { it.start() }

      val httpClient =
        HttpClient(CIO) {
          install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

      tokenProvider =
        TokenProvider(
          httpClient = httpClient,
          discovery = httpClient.get(mockServer.wellKnownUrl("pillarbox-realm").toString()).body(),
          authConfig =
            AuthConfig(
              issuer = mockServer.issuerUrl("pillarbox-realm").toString(),
              clientId = "test-client",
              clientSecret = "test-secret",
              realm = "pillarbox-realm",
            ),
        )
    }

    afterTest {
      mockServer.shutdown()
    }

    should("return token response on successful refresh") {
      mockServer.enqueueCallback(DefaultOAuth2TokenCallback(issuerId = "pillarbox-realm", subject = "test-user"))

      val result = tokenProvider.refresh("valid-refresh-token")

      result.shouldNotBeNull()
      result.accessToken.shouldNotBeBlank()
    }

    should("return null when the provider rejects the refresh token") {
      val result = tokenProvider.refresh("expired-refresh-token")
      result.shouldBeNull()
    }

    should("return null when the token endpoint is unreachable") {
      mockServer.shutdown()

      val result = tokenProvider.refresh("valid-refresh-token")

      result.shouldBeNull()
    }
  })
