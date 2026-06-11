package ch.srgssr.pillarbox.backend.auth

import ch.srgssr.pillarbox.backend.db.EncryptionService
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
import com.auth0.jwt.JWT
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Manages the lifecycle of user sessions, providing validation logic optimized for performance with
 * periodic token verification.
 *
 * @property repository The persistent storage for session data.
 * @property encryptionService Used to hash the cookie session id into its persisted form.
 * @property tokenProvider Used to exchange an expired session's refresh token for new tokens.
 * against the identity provider.
 */
class SessionManager(
  private val repository: SessionRepository,
  private val encryptionService: EncryptionService,
  private val userManager: UserManager,
  private val tokenProvider: TokenProvider,
) {
  companion object {
    val logger = logger()
  }

  suspend fun validate(sessionId: SessionId): Session? {
    val session = repository.find(sessionId.hashedValue)
    return when {
      session == null -> {
        null.also { logger.info { "Session validation failed: session not found in repository." } }
      }

      !session.expired -> {
        session
      }

      else -> {
        logger.info { "Session ${session.publicId} expired at ${session.expiresAt}" }
        refreshOrInvalidate(session)
      }
    }
  }

  /**
   * Attempts to renew an expired session using its stored refresh token.
   * If no refresh token is present or the token endpoint rejects the request,
   * the session is deleted and `null` is returned.
   *
   * @param session The expired session to renew.
   * @return The refreshed [Session] with updated tokens and expiry, or `null` on failure.
   */
  private suspend fun refreshOrInvalidate(session: Session): Session? {
    if (session.refreshToken == null) {
      return null.also {
        logger.info { "Session ${session.publicId} has no refresh token. Deleting." }
        repository.delete(session.sessionId)
      }
    }

    return tokenProvider.refresh(session.refreshToken)?.let { tokenResponse ->
      session.refreshWithToken(tokenResponse).also {
        userManager.upsert(JWT.decode(it.accessToken))
        repository.save(it)
        logger.info { "Session ${it.publicId} successfully refreshed." }
      }
    } ?: run {
      repository.delete(session.sessionId)
      null
    }
  }

  /**
   * Creates an updated copy of the [Session] using the provided [tokenResponse].
   **/
  private fun Session.refreshWithToken(
    tokenResponse: TokenRefreshResponse,
    now: Instant = Clock.System.now(),
  ): Session =
    copy(
      accessToken = tokenResponse.accessToken,
      refreshToken = tokenResponse.refreshToken ?: refreshToken,
      idToken = tokenResponse.idToken ?: idToken,
      expiresAt = tokenResponse.expiresIn?.let { now + it.seconds } ?: (now + 5.minutes),
    )

  /**
   * The hashed value of the session id that is searchable in the database.
   */
  private val SessionId.hashedValue get() = encryptionService.hash(value)
}
