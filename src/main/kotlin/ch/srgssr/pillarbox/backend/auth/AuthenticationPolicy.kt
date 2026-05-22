package ch.srgssr.pillarbox.backend.auth

import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.log.warn
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.HttpMethod
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import java.net.URI

/**
 * Manages authentication policies, providing configuration for OAuth2 settings
 * and JWT validation logic based on OpenID Connect discovery.
 *
 * @property authConfig The application's OIDC credentials, used for client authentication.
 * @property discovery The [OpenIDDiscovery] containing provider-specific endpoints and metadata.
 */
class AuthenticationPolicy(
  val authConfig: AuthConfig,
  val discovery: OpenIDDiscovery,
) {
  companion object {
    val logger = logger()
  }

  /**
   * Provider for JSON Web Keys (JWK) used to verify the signature of incoming JWTs.
   * Built using the `jwks_uri` obtained from the discovery document.
   */
  val jwkProvider: JwkProvider = JwkProviderBuilder(URI(discovery.jwksUri).toURL()).build()

  /**
   * Constructs the [OAuthServerSettings.OAuth2ServerSettings] required for Ktor's OAuth feature.
   *
   * @param name A descriptive name for the OAuth setting.
   * @return A configured OAuth2 server setting instance.
   */
  fun getOAuthSettings(name: String) =
    OAuthServerSettings.OAuth2ServerSettings(
      name = name,
      authorizeUrl = discovery.authorizationEndpoint,
      accessTokenUrl = discovery.tokenEndpoint,
      clientId = authConfig.clientId,
      clientSecret = authConfig.clientSecret,
      accessTokenRequiresBasicAuth = false,
      requestMethod = HttpMethod.Post,
      defaultScopes = authConfig.scopes,
    )

  /**
   * Verifies the incoming [JWTCredential] by checking the audience claim.
   *
   * @param credential The credential extracted from the JWT token.
   *
   * @return A [User] if validation passes; null if the audience is invalid.
   */
  fun verifyJwt(credential: JWTCredential): User? {
    if (!credential.payload.audience.contains(authConfig.clientId)) {
      logger.warn {
        "JWT verification failed: Audience mismatch. " +
          "Expected: ${authConfig.clientId}, Found: ${credential.payload.audience}. " +
          "Subject: ${credential.payload.subject}"
      }
      return null
    }

    return User(
      oidcSub = credential.payload.subject,
      displayName = credential.payload.displayName,
      roles = credential.payload.roles,
    )
  }
}
