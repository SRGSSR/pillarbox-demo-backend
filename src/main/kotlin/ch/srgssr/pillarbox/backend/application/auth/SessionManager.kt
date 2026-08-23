package ch.srgssr.pillarbox.backend.application.auth

import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.domain.model.SessionId
import ch.srgssr.pillarbox.backend.domain.port.IdentityProvider
import ch.srgssr.pillarbox.backend.domain.port.SessionCatalog
import ch.srgssr.pillarbox.backend.domain.port.TokenRefreshResponse
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
import com.auth0.jwt.JWT
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Manages the lifecycle of user sessions: validation, token refresh, and invalidation.
 *
 * @property catalog Port used to read and write persisted sessions.
 * @property tokenProvider Port used to exchange an expired session's refresh token.
 * @property userManager Used to upsert the user carried by a refreshed access token.
 * @property clock The clock session expiry is evaluated with.
 */
class SessionManager(
  private val catalog: SessionCatalog,
  private val tokenProvider: IdentityProvider,
  private val userManager: UserManager,
  private val clock: Clock = Clock.System,
) {
  companion object {
    val logger = logger()
  }

  /**
   * Resolves the session belonging to a session id, refreshing it when expired.
   *
   * @param sessionId The raw session id held in the user's cookie.
   * @return The valid session, or `null` when unknown or beyond recovery.
   */
  suspend fun validate(sessionId: SessionId): Session? {
    val session = catalog.find(sessionId)
    return when {
      session == null -> {
        null.also { logger.info { "Session validation failed: session not found." } }
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
   * If no refresh token is present or the identity provider rejects the request,
   * the session is deleted and `null` is returned.
   *
   * @param session The expired session to renew.
   * @return The refreshed [Session] with updated tokens and expiry, or `null` on failure.
   */
  private suspend fun refreshOrInvalidate(session: Session): Session? {
    if (session.refreshToken == null) {
      return null.also {
        logger.info { "Session ${session.publicId} has no refresh token. Deleting." }
        catalog.delete(session.sessionId)
      }
    }

    return tokenProvider.refresh(session.refreshToken)?.let { tokenResponse ->
      session.refreshWithToken(tokenResponse).also {
        userManager.upsert(JWT.decode(it.accessToken))
        catalog.save(it)
        logger.info { "Session ${it.publicId} successfully refreshed." }
      }
    } ?: run {
      catalog.delete(session.sessionId)
      null
    }
  }

  /**
   * Creates an updated copy of the [Session] using the provided [tokenResponse].
   *
   * @param tokenResponse The tokens issued by the identity provider.
   * @param now The instant the new expiry is computed from.
   * @return The session carrying the new tokens and expiry.
   */
  private fun Session.refreshWithToken(
    tokenResponse: TokenRefreshResponse,
    now: Instant = clock.now(),
  ): Session =
    copy(
      accessToken = tokenResponse.accessToken,
      refreshToken = tokenResponse.refreshToken ?: refreshToken,
      idToken = tokenResponse.idToken ?: idToken,
      expiresAt = tokenResponse.expiresIn?.let { now + it.seconds } ?: (now + 5.minutes),
    )
}
