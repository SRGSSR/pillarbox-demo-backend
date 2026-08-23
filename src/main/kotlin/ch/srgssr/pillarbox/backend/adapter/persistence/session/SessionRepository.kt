package ch.srgssr.pillarbox.backend.adapter.persistence.session

import ch.srgssr.pillarbox.backend.adapter.persistence.EncryptionService
import ch.srgssr.pillarbox.backend.adapter.persistence.ExposedRepository
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.domain.model.SessionId
import ch.srgssr.pillarbox.backend.domain.port.SessionCatalog
import ch.srgssr.pillarbox.backend.time.toKotlinInstant
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.time.Clock

/**
 * Repository responsible for the persistence and retrieval of [Session] entities using Exposed.
 *
 * This implementation maps the [Session] domain model to the [SessionTable] schema.
 *
 * Token columns are encrypted at rest via the [EncryptionService], transparently to callers.
 * Cookie ids are hashed into the stored session key, so the raw credential never
 * reaches the database.
 *
 * @param db The [Database] instance used for all transactions.
 * @param encryptionService Service used to encrypt the stored tokens and hash cookie ids.
 * @param clock The clock session expiry is evaluated with.
 */
class SessionRepository(
  db: Database,
  private val encryptionService: EncryptionService,
  private val clock: Clock = Clock.System,
) : ExposedRepository<Session, String>(db = db, table = SessionTable, idColumn = SessionTable.sessionId),
  SessionCatalog {
  /**
   * Decodes a [ResultRow] from the [SessionTable] into a [Session] domain object.
   */
  override fun ResultRow.decode() =
    Session(
      sessionId = this[SessionTable.sessionId],
      publicId = this[SessionTable.publicId],
      accessToken = encryptionService.decrypt(this[SessionTable.accessToken]),
      refreshToken = this[SessionTable.refreshToken]?.let { encryptionService.decrypt(it) },
      idToken = this[SessionTable.idToken]?.let { encryptionService.decrypt(it) },
      expiresAt = this[SessionTable.expiresAt].toKotlinInstant(),
      oidcSub = this[SessionTable.oidcSub],
    )

  /**
   * Encodes a [Session] domain object into an [UpdateBuilder] for inserts or upserts.
   */
  override fun Table.encode(
    builder: UpdateBuilder<*>,
    item: Session,
  ) {
    builder[SessionTable.sessionId] = item.sessionId
    builder[SessionTable.publicId] = item.publicId
    builder[SessionTable.accessToken] = encryptionService.encrypt(item.accessToken)
    builder[SessionTable.refreshToken] = item.refreshToken?.let { encryptionService.encrypt(it) }
    builder[SessionTable.idToken] = item.idToken?.let { encryptionService.encrypt(it) }
    builder[SessionTable.expiresAt] = item.expiresAt.toUtcOffsetDateTime()
    builder[SessionTable.oidcSub] = item.oidcSub
  }

  /**
   * Persists a new session, replacing the raw cookie id it carries by its hash.
   *
   * @param session The session to persist, keyed by the raw cookie id.
   * @return The persisted session, carrying its stored key.
   */
  override suspend fun open(session: Session): Session =
    save(session.copy(sessionId = encryptionService.hash(session.sessionId)))

  /**
   * Finds the session stored under the hash of the given session id.
   *
   * @param sessionId The raw session id held in the user's cookie.
   * @return The session, or `null` if none matches.
   */
  override suspend fun find(sessionId: SessionId): Session? = find(encryptionService.hash(sessionId.value))

  /**
   * Retrieves the unexpired sessions of a user, most recently refreshed first.
   *
   * Sessions are re-stamped with a fresh expiry on every update, so ordering by
   * expiry yields the most recently updated first.
   *
   * @param oidcSub The OIDC subject of the user.
   * @param slice The window of the result to return.
   * @return The active sessions of the user.
   */
  override suspend fun activeSessionsOf(
    oidcSub: String,
    slice: QuerySlice,
  ): List<Session> =
    getAll(
      limit = slice.limit,
      offset = slice.offset,
      filter = {
        (SessionTable.oidcSub eq oidcSub) and
          (SessionTable.expiresAt greater clock.now().toUtcOffsetDateTime())
      },
      sort = listOf(SessionTable.expiresAt to SortOrder.DESC),
    ).toList()
}
