package ch.srgssr.pillarbox.backend.persistence.session

import ch.srgssr.pillarbox.backend.db.ExposedRepository
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.time.toKotlinInstant
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Repository responsible for the persistence and retrieval of [Session] entities using Exposed.
 *
 * This implementation maps the [Session] domain model to the [SessionTable] schema.
 *
 * @param db The [Database] instance used for all transactions.
 */
class SessionRepository(
  db: Database,
) : ExposedRepository<Session, String>(db = db, table = SessionTable, idColumn = SessionTable.sessionId) {
  /**
   * Decodes a [ResultRow] from the [SessionTable] into a [Session] domain object.
   */
  override fun ResultRow.decode() =
    Session(
      sessionId = this[SessionTable.sessionId],
      accessToken = this[SessionTable.accessToken],
      refreshToken = this[SessionTable.refreshToken],
      idToken = this[SessionTable.idToken],
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
    builder[SessionTable.accessToken] = item.accessToken
    builder[SessionTable.refreshToken] = item.refreshToken
    builder[SessionTable.idToken] = item.idToken
    builder[SessionTable.expiresAt] = item.expiresAt.toUtcOffsetDateTime()
    builder[SessionTable.oidcSub] = item.oidcSub
  }
}
