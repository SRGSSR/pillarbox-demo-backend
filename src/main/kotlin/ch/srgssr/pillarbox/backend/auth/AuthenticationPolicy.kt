package ch.srgssr.pillarbox.backend.auth

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
 * @property clientId The unique identifier for the client application.
 * @property clientSecret The secret key used for authenticating the client with the identity provider.
 * @property discovery The [OpenIDDiscovery] containing provider-specific endpoints and metadata.
 */
class AuthenticationPolicy(
  val clientId: String,
  val clientSecret: String,
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
   * * Defaults to using [HttpMethod.Post] for token requests and requests standard OpenID scopes.
   *
   * @param name A descriptive name for the OAuth setting.
   * @return A configured OAuth2 server setting instance.
   */
  fun getOAuthSettings(name: String) =
    OAuthServerSettings.OAuth2ServerSettings(
      name = name,
      authorizeUrl = discovery.authorizationEndpoint,
      accessTokenUrl = discovery.tokenEndpoint,
      clientId = clientId,
      clientSecret = clientSecret,
      accessTokenRequiresBasicAuth = false,
      requestMethod = HttpMethod.Post,
      defaultScopes = listOf("openid", "profile", "email"),
    )

  /**
   * Verifies the incoming [JWTCredential] by checking the audience claim.
   *
   * @param credential The credential extracted from the JWT token.
   *
   * @return A [JWTPrincipal] if validation passes; null if the audience is invalid.
   */
  fun verifyJwt(credential: JWTCredential): JWTPrincipal? {
    val audience = credential.payload.audience
    return if (audience.contains(clientId)) {
      JWTPrincipal(credential.payload)
    } else {
      logger.warn {
        "JWT verification failed: Audience mismatch. " +
          "Expected: $clientId, Found: $audience. " +
          "Subject: ${credential.payload.subject}"
      }
      null
    }
  }
}
