package ch.srgssr.pillarbox.backend.auth

import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.log.warn
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the lifecycle of user sessions, providing validation logic optimized for performance with
 * periodic token verification.
 *
 * @property repository The persistent storage for session data.
 * @property userInfoProvider Used to verify token validity and synchronise user profile data
 * against the identity provider during periodic re-validation.
 * @property validationIntervalSeconds The frequency at which the access token must be re-verified
 * against the identity provider.
 */
class SessionManager(
  private val repository: SessionRepository,
  private val userInfoProvider: UserInfoProvider,
  private val validationIntervalSeconds: Long,
) {
  companion object {
    val logger = logger()
  }

  /**
   * Validates a session. If the session exists and has been checked recently (within [validationIntervalSeconds]),
   * it is returned immediately. Otherwise, the associated access token is verified against the OIDC provider
   * and the linked user's profile is synchronised.
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

    return if (session.expired) {
      logger.warn { "Session $sessionId expired at ${session.expiresAt}" }
      repository.delete(session.sessionId)
      null
    } else {
      verifySession(session)
    }
  }

  /**
   * Verifies the integrity of a session by checking its local expiration and
   * remote OIDC token validity. When a remote check is performed, the linked user's
   * profile is also synchronised from the identity provider.
   *
   * @param session The current session data retrieved from the repository.
   * @return The valid (and potentially updated) [Session], or `null` if the session is invalid.
   */
  private suspend fun verifySession(session: Session): Session? {
    if (session.valid) return session

    logger.info { "Re-validating OIDC token for session: ${session.sessionId}" }

    return userInfoProvider.fetchAndSync(session.accessToken)?.let {
      session.copy(lastChecked = Clock.System.now()).also { updated ->
        repository.save(updated)
        logger.info { "Session ${session.sessionId} successfully re-validated." }
      }
    } ?: run {
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
}
