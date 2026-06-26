package ch.srgssr.pillarbox.backend.persistence.team

import ch.srgssr.pillarbox.backend.db.ExposedRepository
import ch.srgssr.pillarbox.backend.domain.model.Role.Companion.toRole
import ch.srgssr.pillarbox.backend.domain.model.Team
import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.persistence.user.UserTable
import ch.srgssr.pillarbox.backend.time.toKotlinInstant
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpsertBuilder
import org.jetbrains.exposed.v1.jdbc.Database
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
 */
class TeamRepository(
  db: Database,
) : ExposedRepository<Team, String>(db = db, table = TeamTable, idColumn = TeamTable.id) {
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
    builder[TeamTable.createdAt] = Clock.System.now().toUtcOffsetDateTime()
    builder[TeamTable.updatedAt] = Clock.System.now().toUtcOffsetDateTime()
  }

  /**
   * Encodes a [Team] domain object into an [UpdateBuilder] for updates.
   */
  override fun encodeOnUpdate(item: Team): (UpsertBuilder.(UpdateStatement) -> Unit) =
    {
      it[TeamTable.name] = item.name
      it[TeamTable.updatedAt] = Clock.System.now().toUtcOffsetDateTime()
    }

  /**
   * Adds a user to a team. Adding an existing member again is a no-op.
   *
   * @param teamId The ID of the target team.
   * @param oidcSub The OIDC sub of the user to add.
   */
  suspend fun addMember(
    teamId: String,
    oidcSub: String,
  ): Unit =
    query {
      TeamMemberTable.upsert(TeamMemberTable.teamId, TeamMemberTable.oidcSub) {
        it[TeamMemberTable.teamId] = teamId
        it[TeamMemberTable.oidcSub] = oidcSub
        it[TeamMemberTable.addedAt] = Clock.System.now().toUtcOffsetDateTime()
      }
    }

  /**
   * Removes a user from a team.
   *
   * @param teamId The ID of the team.
   * @param oidcSub The OIDC sub of the user to remove.
   * @return `true` if a membership was removed, `false` if the user was not a member.
   */
  suspend fun removeMember(
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
  suspend fun findTeamsOf(oidcSub: String): List<Team> =
    query(readOnly = true) {
      (TeamTable innerJoin TeamMemberTable)
        .selectAll()
        .where { TeamMemberTable.oidcSub eq oidcSub }
        .map { it.decode() }
    }

  /**
   * Retrieves the members of a team as [User] entities, paginated.
   *
   * @param teamId The ID of the team whose members should be retrieved.
   * @param limit The maximum number of members to return.
   * @param offset The number of members to skip for pagination.
   * @return The list of member [User] entities.
   */
  suspend fun findMembers(
    teamId: String,
    limit: Int = 100,
    offset: Long = 0,
  ): List<User> =
    query(readOnly = true) {
      (TeamMemberTable innerJoin UserTable)
        .selectAll()
        .where { TeamMemberTable.teamId eq teamId }
        .limit(limit)
        .offset(offset)
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
   * Counts the members of each of the given teams in a single query.
   *
   * @param teamIds The teams whose members should be counted.
   * @return A map from team id to member count; teams with no members map to `0`.
   */
  suspend fun countMembersOf(vararg teamIds: String): Map<String, Long> =
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
