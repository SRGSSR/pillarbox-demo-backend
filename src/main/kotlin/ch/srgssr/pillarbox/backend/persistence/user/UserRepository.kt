package ch.srgssr.pillarbox.backend.persistence.user

import ch.srgssr.pillarbox.backend.db.ExposedRepository
import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.time.toKotlinInstant
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpsertBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.time.Clock

/**
 * Repository responsible for the persistence and retrieval of [User] entities using Exposed.
 *
 * This implementation maps the [User] domain model to the [UserTable] schema.
 *
 * @param db The [Database] instance used for all transactions.
 */
class UserRepository(
  db: Database,
) : ExposedRepository<User, String>(db = db, table = UserTable, idColumn = UserTable.oidcSub) {
  /**
   * Decodes a [ResultRow] from the [UserTable] into a [User] domain object.
   */
  override fun ResultRow.decode() =
    User(
      oidcSub = this[UserTable.oidcSub],
      displayName = this[UserTable.displayName],
      createdAt = this[UserTable.createdAt].toKotlinInstant(),
      updatedAt = this[UserTable.updatedAt].toKotlinInstant(),
    )

  /**
   * Encodes a [User] domain object into an [UpdateBuilder] for inserts.
   */
  override fun Table.encode(
    builder: UpdateBuilder<*>,
    item: User,
  ) {
    builder[UserTable.oidcSub] = item.oidcSub
    builder[UserTable.displayName] = item.displayName
    builder[UserTable.createdAt] = Clock.System.now().toUtcOffsetDateTime()
    builder[UserTable.updatedAt] = Clock.System.now().toUtcOffsetDateTime()
  }

  /**
   * Encodes a [User] domain object into an [UpdateBuilder] for updates.
   */
  override fun encodeOnUpdate(item: User): (UpsertBuilder.(UpdateStatement) -> Unit) =
    {
      it[UserTable.displayName] = item.displayName
      it[UserTable.updatedAt] = Clock.System.now().toUtcOffsetDateTime()
    }
}
