package ch.srgssr.pillarbox.backend.auth

import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.error
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.log.warn
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the lifecycle of user sessions, providing validation logic optimized for performance with
 * periodic token verification.
 *
 * @property repository The persistent storage for session data.
 * @property httpClient The client used to perform back-channel validation against the OIDC provider.
 * @property userInfoUrl The OIDC endpoint used to verify if an access token is still active.
 * @property validationIntervalSeconds The frequency at which the access token must be re-verified
 * against the identity provider.
 */
class SessionManager(
  private val repository: SessionRepository,
  private val httpClient: HttpClient,
  private val userInfoUrl: String,
  private val validationIntervalSeconds: Long,
) {
  companion object {
    val logger = logger()
  }

  /**
   * Validates a session. If the session exists and has been checked recently (within [validationIntervalSeconds]),
   * it is returned immediately. Otherwise, the associated access token is verified against the OIDC provider.
   *
   * @param sessionId The ID of the session to validate.
   *
   * @return The [Session] if valid; null if the session is expired, missing, or the token is revoked.
   */
  suspend fun validate(sessionId: SessionId): Session? {
    val session =
      repository.find(sessionId.value) ?: run {
        logger.info { "Session validation failed: Session ${sessionId.value} not found in repository." }
        return null
      }

    return verifySession(session)
  }

  /**
   * Verifies the integrity of a session by checking its local expiration and
   * remote OIDC token validity.
   *
   * @param session The current session data retrieved from the repository.
   * @return The valid (and potentially updated) [Session], or `null` if the session is invalid.
   */
  private suspend fun verifySession(session: Session): Session? {
    if (session.valid) return session

    logger.info { "Re-validating OIDC token for session: ${session.sessionId}" }

    return if (session.isTokenValid()) {
      session.copy(lastChecked = Clock.System.now()).also { updated ->
        repository.save(session.sessionId, updated)
        logger.info { "Session ${session.sessionId} successfully re-validated." }
      }
    } else {
      logger.warn { "Session ${session.sessionId} invalidated by OIDC provider." }
      repository.delete(session.sessionId)
      null
    }
  }

  /**
   * Whether the session has not yet exceeded the [validationIntervalSeconds] threshold.
   */
  private val Session.valid: Boolean
    get() {
      val now = Clock.System.now()
      val elapsed = now - lastChecked
      val threshold = validationIntervalSeconds.seconds

      return (elapsed < threshold).also { fresh ->
        if (fresh) {
          val remaining = threshold - elapsed
          logger.debug {
            "Session $sessionId is still valid. " +
              "Elapsed: ${elapsed.inWholeSeconds}s, " +
              "Next check in: ${remaining.inWholeSeconds}s"
          }
        }
      }
    }

  /**
   * Checks if the provided access token is still valid by calling the OIDC UserInfo endpoint.
   *
   * @return True if the provider returns 200 OK; false otherwise.
   */
  private suspend fun Session.isTokenValid(): Boolean =
    try {
      httpClient.get(userInfoUrl) { bearerAuth(accessToken) }.let {
        logger.info { "Token validation check returned status: ${it.status}" }
        it.status == HttpStatusCode.OK
      }
    } catch (e: IOException) {
      logger.error(e) { "Failed to reach OIDC UserInfo endpoint at $userInfoUrl" }
      false
    }
}
