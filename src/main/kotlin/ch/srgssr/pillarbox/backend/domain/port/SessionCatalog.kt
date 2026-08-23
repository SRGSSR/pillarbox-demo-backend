package ch.srgssr.pillarbox.backend.domain.port

import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.domain.model.SessionId

/**
 * Reads and writes authenticated user sessions.
 *
 * Lookups by [SessionId] take the raw cookie credential; the adapter derives the
 * stored key from it, so the raw value never reaches the database.
 */
interface SessionCatalog {
  /**
   * Persists a new session whose [Session.sessionId] carries the raw [SessionId] value.
   *
   * The adapter derives the stored key from the raw id, so only the derived
   * key reaches the database.
   *
   * @param session The session to persist, keyed by the raw session id.
   * @return The persisted session, carrying its stored key.
   */
  suspend fun open(session: Session): Session

  /**
   * Finds the session belonging to a session id.
   *
   * @param sessionId The raw session id held in the user's cookie.
   * @return The session, or `null` if none matches.
   */
  suspend fun find(sessionId: SessionId): Session?

  /**
   * Persists or overwrites a session by its stored key.
   *
   * @param item The session to save.
   * @return The persisted session.
   */
  suspend fun save(item: Session): Session

  /**
   * Deletes a session by its stored key.
   *
   * @param sessionId The stored key of the session, as carried by [Session.sessionId].
   * @return `true` if the session existed and was deleted, `false` otherwise.
   */
  suspend fun delete(sessionId: String): Boolean

  /**
   * Retrieves the unexpired sessions of a user, most recently refreshed first.
   *
   * @param oidcSub The OIDC subject of the user.
   * @param slice The window of the result to return.
   * @return The active sessions of the user.
   */
  suspend fun activeSessionsOf(
    oidcSub: String,
    slice: QuerySlice = QuerySlice(),
  ): List<Session>
}
