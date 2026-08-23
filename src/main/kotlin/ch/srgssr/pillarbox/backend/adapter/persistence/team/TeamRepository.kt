package ch.srgssr.pillarbox.backend.adapter.persistence.team

import ch.srgssr.pillarbox.backend.adapter.persistence.ExposedRepository
import ch.srgssr.pillarbox.backend.adapter.persistence.user.UserTable
import ch.srgssr.pillarbox.backend.domain.catalog.PaginatedResult
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.model.Role.Companion.toRole
import ch.srgssr.pillarbox.backend.domain.model.Team
import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.domain.port.TeamCatalog
import ch.srgssr.pillarbox.backend.time.toKotlinInstant
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpsertBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock

/**
 * Repository responsible for the persistence and retrieval of [Team] entities using Exposed.
 *
 * This implementation maps the [Team] domain model to the [TeamTable] schema and manages
 * team memberships through the [TeamMemberTable] junction table.
 *
 * @param db The [Database] instance used for all transactions.
 * @param clock The clock persisted timestamps are stamped with.
 */
@SuppressWarnings("TooManyFunctions")
class TeamRepository(
  db: Database,
  private val clock: Clock = Clock.System,
) : ExposedRepository<Team, String>(db = db, table = TeamTable, idColumn = TeamTable.id),
  TeamCatalog {
  /**
   * Decodes a [ResultRow] from the [TeamTable] into a [Team] domain object.
   */
  override fun ResultRow.decode() =
    Team(
      id = this[TeamTable.id],
      name = this[TeamTable.name],
      createdAt = this[TeamTable.createdAt].toKotlinInstant(),
      updatedAt = this[TeamTable.updatedAt].toKotlinInstant(),
    )

  /**
   * Encodes a [Team] domain object into an [UpdateBuilder] for inserts.
   */
  override fun Table.encode(
    builder: UpdateBuilder<*>,
    item: Team,
  ) {
    builder[TeamTable.id] = item.id
    builder[TeamTable.name] = item.name
    builder[TeamTable.createdAt] = clock.now().toUtcOffsetDateTime()
    builder[TeamTable.updatedAt] = clock.now().toUtcOffsetDateTime()
  }

  /**
   * Encodes a [Team] domain object into an [UpdateBuilder] for updates.
   */
  override fun encodeOnUpdate(item: Team): (UpsertBuilder.(UpdateStatement) -> Unit) =
    {
      it[TeamTable.name] = item.name
      it[TeamTable.updatedAt] = clock.now().toUtcOffsetDateTime()
    }

  /**
   * Adds a user to a team. Adding an existing member again is a no-op.
   *
   * @param teamId The ID of the target team.
   * @param oidcSub The OIDC sub of the user to add.
   */
  override suspend fun addMember(
    teamId: String,
    oidcSub: String,
  ): Unit =
    query {
      TeamMemberTable.upsert(TeamMemberTable.teamId, TeamMemberTable.oidcSub) {
        it[TeamMemberTable.teamId] = teamId
        it[TeamMemberTable.oidcSub] = oidcSub
        it[TeamMemberTable.addedAt] = clock.now().toUtcOffsetDateTime()
      }
    }

  /**
   * Removes a user from a team.
   *
   * @param teamId The ID of the team.
   * @param oidcSub The OIDC sub of the user to remove.
   * @return `true` if a membership was removed, `false` if the user was not a member.
   */
  override suspend fun removeMember(
    teamId: String,
    oidcSub: String,
  ): Boolean =
    query {
      TeamMemberTable.deleteWhere {
        (TeamMemberTable.teamId eq teamId) and (TeamMemberTable.oidcSub eq oidcSub)
      } > 0
    }

  /**
   * Retrieves all teams the given user is a member of.
   *
   * @param oidcSub The OIDC sub of the user.
   * @return The list of [Team] entities, empty if the user belongs to no team.
   */
  override suspend fun findTeamsOf(oidcSub: String): List<Team> =
    query(readOnly = true) {
      (TeamTable innerJoin TeamMemberTable)
        .selectAll()
        .where { TeamMemberTable.oidcSub eq oidcSub }
        .map { it.decode() }
    }

  /**
   * Retrieves a page of teams, most recently updated first, optionally narrowed by a
   * case-insensitive name match.
   *
   * @param text Optional filter on the team name; `null` or blank lists every team.
   * @param slice The window of the result to return.
   * @return A [PaginatedResult] with the matching teams and the total number of matches.
   */
  override suspend fun page(
    text: String?,
    slice: QuerySlice,
  ): PaginatedResult<Team> =
    getAllPaginated(
      limit = slice.limit,
      offset = slice.offset,
      filter =
        text?.takeIf { it.isNotBlank() }?.let {
          { TeamTable.name.lowerCase() like "%${it.lowercase()}%" }
        },
      sort = listOf(TeamTable.updatedAt to SortOrder.DESC),
    )

  /**
   * Retrieves the teams matching the given identifiers; unknown ids are absent.
   *
   * @param ids The team identifiers to look up.
   * @return The existing teams among [ids].
   */
  override suspend fun list(ids: Collection<String>): List<Team> {
    val teamIds = ids.distinct()
    if (teamIds.isEmpty()) return emptyList()
    return getAll(limit = teamIds.size, filter = { TeamTable.id inList teamIds }).toList()
  }

  /**
   * Retrieves the members of a team as [User] entities, paginated.
   *
   * @param teamId The ID of the team whose members should be retrieved.
   * @param slice The window of the result to return.
   * @return The list of member [User] entities.
   */
  override suspend fun findMembers(
    teamId: String,
    slice: QuerySlice,
  ): List<User> =
    query(readOnly = true) {
      (TeamMemberTable innerJoin UserTable)
        .selectAll()
        .where { TeamMemberTable.teamId eq teamId }
        .limit(slice.limit)
        .offset(slice.offset)
        .map { row ->
          User(
            oidcSub = row[UserTable.oidcSub],
            displayName = row[UserTable.displayName],
            roles = row[UserTable.roles].mapNotNull { it.toRole() }.toSet(),
            createdAt = row[UserTable.createdAt].toKotlinInstant(),
            updatedAt = row[UserTable.updatedAt].toKotlinInstant(),
          )
        }
    }

  /**
   * Replaces the entire membership of a team in a single transaction: every current member is removed
   * and [memberOidcSubs] are inserted as the team's members in one batch.
   *
   * @param teamId The team whose members are replaced.
   * @param memberOidcSubs The OIDC subs the team should have as members; blanks and duplicates are ignored.
   */
  override suspend fun replaceMembers(
    teamId: String,
    memberOidcSubs: Collection<String>,
  ): Unit =
    query {
      TeamMemberTable.deleteWhere { TeamMemberTable.teamId eq teamId }
      val subs = memberOidcSubs.filter { it.isNotBlank() }.distinct()
      if (subs.isNotEmpty()) {
        val addedAt = clock.now().toUtcOffsetDateTime()
        TeamMemberTable.batchInsert(subs) { sub ->
          this[TeamMemberTable.teamId] = teamId
          this[TeamMemberTable.oidcSub] = sub
          this[TeamMemberTable.addedAt] = addedAt
        }
      }
    }

  /**
   * Counts the members of each of the given teams in a single query.
   *
   * @param teamIds The teams whose members should be counted.
   * @return A map from team id to member count; teams with no members map to `0`.
   */
  override suspend fun countMembersOf(vararg teamIds: String): Map<String, Long> =
    query(readOnly = true) {
      val named = teamIds.distinct()
      val results = named.associateWith { 0L }.toMutableMap()
      if (named.isNotEmpty()) {
        val memberCount = TeamMemberTable.oidcSub.count()
        TeamMemberTable
          .select(TeamMemberTable.teamId, memberCount)
          .where { TeamMemberTable.teamId inList named }
          .groupBy(TeamMemberTable.teamId)
          .forEach { row ->
            results[row[TeamMemberTable.teamId]] = row[memberCount]
          }
      }
      results
    }
}
