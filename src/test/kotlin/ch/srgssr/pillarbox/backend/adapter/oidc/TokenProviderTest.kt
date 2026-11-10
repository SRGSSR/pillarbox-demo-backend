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
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback

class TokenProviderTest :
  ShouldSpec({
    lateinit var mockServer: MockOAuth2Server
    lateinit var tokenProvider: TokenProvider
    lateinit var httpClient: HttpClient

    beforeTest {
      mockServer = mockOAuth2Server().also { it.start() }

      httpClient =
        HttpClient(CIO) {
          followRedirects = false
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
      val refreshToken = issueRefreshToken(httpClient, mockServer)

      val result = tokenProvider.refresh(refreshToken)

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

private const val ISSUER_ID = "pillarbox-realm"
private const val CLIENT_ID = "test-client"
private const val REDIRECT_URI = "http://localhost/callback"

/**
 * Runs the authorization code flow against the mock server to obtain a refresh token it accepts.
 * The mock server only accepts refresh tokens it issued itself.
 *
 * @param httpClient The HTTP client used for the flow. It must not follow redirects.
 * @param mockServer The mock OAuth2 server that issues the tokens.
 *
 * @returns The refresh token issued by the token endpoint.
 */
private suspend fun issueRefreshToken(
  httpClient: HttpClient,
  mockServer: MockOAuth2Server,
): String {
  val authorization =
    httpClient.get(mockServer.authorizationEndpointUrl(ISSUER_ID).toString()) {
      parameter("response_type", "code")
      parameter("client_id", CLIENT_ID)
      parameter("redirect_uri", REDIRECT_URI)
      parameter("scope", "openid")
      parameter("state", "test-state")
    }
  val code =
    checkNotNull(Url(checkNotNull(authorization.headers[HttpHeaders.Location])).parameters["code"])

  val tokens: JsonObject =
    httpClient
      .submitForm(
        url = mockServer.tokenEndpointUrl(ISSUER_ID).toString(),
        formParameters =
          parameters {
            append("grant_type", "authorization_code")
            append("code", code)
            append("redirect_uri", REDIRECT_URI)
            append("client_id", CLIENT_ID)
            append("client_secret", "test-secret")
          },
      ).body()

  return checkNotNull(tokens["refresh_token"]).jsonPrimitive.content
}
