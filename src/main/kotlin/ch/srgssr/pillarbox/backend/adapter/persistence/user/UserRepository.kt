package ch.srgssr.pillarbox.backend.adapter.persistence.user

import ch.srgssr.pillarbox.backend.adapter.persistence.ExposedRepository
import ch.srgssr.pillarbox.backend.domain.catalog.PaginatedResult
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.model.Role.Companion.toRole
import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.domain.port.UserCatalog
import ch.srgssr.pillarbox.backend.time.toKotlinInstant
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
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
 * @param clock The clock persisted timestamps are stamped with.
 */
class UserRepository(
  db: Database,
  private val clock: Clock = Clock.System,
) : ExposedRepository<User, String>(db = db, table = UserTable, idColumn = UserTable.oidcSub),
  UserCatalog {
  /**
   * Decodes a [ResultRow] from the [UserTable] into a [User] domain object.
   */
  override fun ResultRow.decode() =
    User(
      oidcSub = this[UserTable.oidcSub],
      displayName = this[UserTable.displayName],
      roles = this[UserTable.roles].mapNotNull { it.toRole() }.toSet(),
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
    builder[UserTable.roles] = item.roles.map { it.key }
    builder[UserTable.createdAt] = clock.now().toUtcOffsetDateTime()
    builder[UserTable.updatedAt] = clock.now().toUtcOffsetDateTime()
  }

  /**
   * Encodes a [User] domain object into an [UpdateBuilder] for updates.
   */
  override fun encodeOnUpdate(item: User): (UpsertBuilder.(UpdateStatement) -> Unit) =
    {
      it[UserTable.displayName] = item.displayName
      it[UserTable.roles] = item.roles.map { role -> role.key }
      it[UserTable.updatedAt] = clock.now().toUtcOffsetDateTime()
    }

  /**
   * Retrieves a page of users, most recently updated first, optionally narrowed by a
   * case-insensitive display-name match.
   *
   * @param text Optional filter on the display name; `null` or blank lists everyone.
   * @param slice The window of the result to return.
   * @return A [PaginatedResult] with the matching users and the total number of matches.
   */
  override suspend fun page(
    text: String?,
    slice: QuerySlice,
  ): PaginatedResult<User> =
    getAllPaginated(
      limit = slice.limit,
      offset = slice.offset,
      filter =
        text?.takeIf { it.isNotBlank() }?.let {
          { UserTable.displayName.lowerCase() like "%${it.lowercase()}%" }
        },
      sort = listOf(UserTable.updatedAt to SortOrder.DESC),
    )

  /**
   * Retrieves the users matching the given OIDC subjects; unknown subjects are absent.
   *
   * @param oidcSubs The OIDC subjects to look up.
   * @return The existing users among [oidcSubs].
   */
  override suspend fun list(oidcSubs: Collection<String>): List<User> {
    val subs = oidcSubs.distinct()
    if (subs.isEmpty()) return emptyList()
    return getAll(limit = subs.size, filter = { UserTable.oidcSub inList subs }).toList()
  }
}
