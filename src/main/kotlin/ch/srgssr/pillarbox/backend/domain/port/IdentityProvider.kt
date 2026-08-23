package ch.srgssr.pillarbox.backend.domain.port

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the token endpoint response from an OIDC provider.
 *
 * @property accessToken The new access token.
 * @property refreshToken The new refresh token, if the provider rotates them.
 * @property idToken The new ID token, if included in the response.
 * @property expiresIn Validity period of [accessToken] in seconds.
 */
@Serializable
data class TokenRefreshResponse(
  @SerialName("access_token") val accessToken: String,
  @SerialName("refresh_token") val refreshToken: String? = null,
  @SerialName("id_token") val idToken: String? = null,
  @SerialName("expires_in") val expiresIn: Long? = null,
)

/**
 * The OIDC identity provider the application authenticates against.
 */
interface IdentityProvider {
  /**
   * Exchanges a refresh token for a new set of tokens.
   *
   * @param refreshToken The refresh token issued during the original authorization.
   * @return The new tokens, or `null` if the provider rejects the token or is unreachable.
   */
  suspend fun refresh(refreshToken: String): TokenRefreshResponse?
}
