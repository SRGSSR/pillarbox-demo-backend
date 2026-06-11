package ch.srgssr.pillarbox.backend.persistence.session

import ch.srgssr.pillarbox.backend.db.EncryptionService
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
 * Token columns are encrypted at rest via the [EncryptionService], transparently to callers.
 *
 * @param db The [Database] instance used for all transactions.
 * @param encryptionService Service used to encrypt and decrypt the stored tokens.
 */
class SessionRepository(
  db: Database,
  private val encryptionService: EncryptionService,
) : ExposedRepository<Session, String>(db = db, table = SessionTable, idColumn = SessionTable.sessionId) {
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
}
