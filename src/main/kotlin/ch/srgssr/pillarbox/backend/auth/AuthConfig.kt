package ch.srgssr.pillarbox.backend.auth

import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Configuration parameters for the Authentication provider.
 *
 * @property issuer The base URL of the identity provider.
 * @property clientId The unique identifier for the application (audience).
 * @property clientSecret The secret key used for server-side token exchange.
 * @property realm The authentication realm used in the challenge header.
 */
data class AuthConfig(
  val issuer: String,
  val clientId: String,
  val clientSecret: String,
  val realm: String,
) {
  /**
   * The standard URL where the OIDC discovery metadata can be found.
   */
  val discoveryUrl = "$issuer/.well-known/openid-configuration"
  val userInfoUrl = "$issuer/protocol/openid-connect/userinfo"
}

/**
 * Represents the OpenID Connect discovery document.
 */
@Serializable
data class OpenIDDiscovery(
  @SerialName("authorization_endpoint") val authorizationEndpoint: String,
  @SerialName("token_endpoint") val tokenEndpoint: String,
  @SerialName("jwks_uri") val jwksUri: String,
)

/**
 * Extracts [AuthConfig] from the application's configuration file.
 *
 * @return A populated [AuthConfig] instance.
 */
fun ApplicationConfig.toAuthConfig(): AuthConfig {
  val auth = config("oidc")

  return AuthConfig(
    issuer = auth.property("issuer").getString(),
    clientId = auth.property("client_id").getString(),
    clientSecret = auth.propertyOrNull("secret")?.getString() ?: "",
    realm = auth.property("realm").getString(),
  )
}
