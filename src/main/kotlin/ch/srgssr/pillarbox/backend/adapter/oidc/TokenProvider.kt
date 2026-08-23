package ch.srgssr.pillarbox.backend.adapter.oidc

import ch.srgssr.pillarbox.backend.application.auth.AuthConfig
import ch.srgssr.pillarbox.backend.application.auth.OpenIDDiscovery
import ch.srgssr.pillarbox.backend.domain.port.IdentityProvider
import ch.srgssr.pillarbox.backend.domain.port.TokenRefreshResponse
import ch.srgssr.pillarbox.backend.log.error
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.io.IOException

/**
 * Exchanges a refresh token for a new set of tokens via the OIDC token endpoint.
 *
 * @property httpClient The HTTP client used to call the token endpoint.
 * @property discovery The OIDC discovery document, used to resolve [OpenIDDiscovery.tokenEndpoint].
 * @property authConfig The application's OIDC credentials, used for client authentication.
 */
class TokenProvider(
  private val httpClient: HttpClient,
  private val discovery: OpenIDDiscovery,
  private val authConfig: AuthConfig,
) : IdentityProvider {
  companion object {
    val logger = logger()
  }

  /**
   * Requests new tokens from the OIDC token endpoint using the provided [refreshToken].
   *
   * @param refreshToken The refresh token issued during the original authorization.
   *
   * @return A [TokenRefreshResponse] on success, or `null` if the provider rejects the token
   *         or the endpoint is unreachable.
   */
  override suspend fun refresh(refreshToken: String): TokenRefreshResponse? =
    try {
      val response =
        httpClient.submitForm(
          url = discovery.tokenEndpoint,
          formParameters =
            parameters {
              append("grant_type", "refresh_token")
              append("refresh_token", refreshToken)
              append("client_id", authConfig.clientId)
              append("client_secret", authConfig.clientSecret)
            },
        )
      logger.info { "Token endpoint returned status: ${response.status}" }
      if (response.status == HttpStatusCode.OK) response.body() else null
    } catch (e: IOException) {
      logger.error(e) { "Failed to reach OIDC token endpoint at ${discovery.tokenEndpoint}" }
      null
    }
}
