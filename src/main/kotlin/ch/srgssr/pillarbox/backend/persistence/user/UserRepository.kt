package ch.srgssr.pillarbox.backend.persistence.user

import ch.srgssr.pillarbox.backend.db.ExposedRepository
import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.time.toKotlinInstant
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Repository responsible for the persistence and retrieval of [User] entities using Exposed.
 *
 * This implementation maps the [User] domain model to the [UserTable] schema.
 *
 * @param db The [Database] instance used for all transactions.
 */
class UserRepository(
  db: Database,
) : ExposedRepository<User, String>(db = db, table = UserTable, idColumn = UserTable.id) {
  /**
   * Decodes a [ResultRow] from the [UserTable] into a [User] domain object.
   */
  override fun ResultRow.decode() =
    User(
      id = this[UserTable.id],
      oidcSub = this[UserTable.oidcSub],
      displayName = this[UserTable.displayName],
      createdAt = this[UserTable.createdAt].toKotlinInstant(),
      updatedAt = this[UserTable.updatedAt].toKotlinInstant(),
      lastLoginAt = this[UserTable.lastLoginAt].toKotlinInstant(),
    )

  /**
   * Encodes a [User] domain object into an [UpdateBuilder] for inserts or upserts.
   */
  override fun Table.encode(
    builder: UpdateBuilder<*>,
    item: User,
  ) {
    builder[UserTable.id] = item.id
    builder[UserTable.oidcSub] = item.oidcSub
    builder[UserTable.displayName] = item.displayName
    builder[UserTable.createdAt] = item.createdAt.toUtcOffsetDateTime()
    builder[UserTable.updatedAt] = item.updatedAt.toUtcOffsetDateTime()
    builder[UserTable.lastLoginAt] = item.lastLoginAt.toUtcOffsetDateTime()
  }

  /**
   * Finds a [User] by their OIDC subject identifier.
   *
   * @param sub The OIDC `sub` claim uniquely identifying the user at the identity provider.
   * @return The [User] if found, or `null` if no match exists.
   */
  suspend fun findByOidcSub(sub: String): User? = findOne { UserTable.oidcSub eq sub }
}
