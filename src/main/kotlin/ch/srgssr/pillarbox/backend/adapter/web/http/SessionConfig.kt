package ch.srgssr.pillarbox.backend.adapter.web.http

import io.ktor.server.config.ApplicationConfig

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
