package ch.srgssr.pillarbox.backend.auth

import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.Serializable

/**
 * Configuration parameters for session management and cookie security.
 *
 * @property cookieSecret The secret key used to sign the session cookie to prevent tampering.
 * @property timeoutSeconds The total duration (TTL) a session cookie remains valid.
 * @property validationIntervalSeconds The interval at which the session must be re-verified
 * against the OIDC provider.
 * @property isSecure Whether the session cookie should only be sent over HTTPS.
 */
data class SessionConfig(
  val cookieSecret: String,
  val timeoutSeconds: Long,
  val validationIntervalSeconds: Long,
  val isSecure: Boolean = true,
)

/**
 * Extracts [SessionConfig] from the application's configuration file.
 *
 * @return A populated [SessionConfig] instance.
 */
fun ApplicationConfig.toSessionConfig(): SessionConfig {
  val session = config("session")
  return SessionConfig(
    cookieSecret = session.property("cookieSecret").getString(),
    timeoutSeconds = session.property("timeoutSeconds").getString().toLong(),
    validationIntervalSeconds = session.property("validationIntervalSeconds").getString().toLong(),
    isSecure = session.propertyOrNull("secure")?.getString()?.toBoolean() ?: true,
  )
}

/**
 * Type-safe wrapper for a session identifier used within the session cookie.
 *
 * This distinguishes the session ID from other string-based data during
 * serialization and cookie handling in Ktor.
 *
 * @property value The raw session ID string stored in the cookie.
 */
@Serializable
data class SessionId(
  val value: String,
)
