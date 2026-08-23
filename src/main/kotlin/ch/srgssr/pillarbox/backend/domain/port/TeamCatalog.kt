package ch.srgssr.pillarbox.backend.domain.port

import ch.srgssr.pillarbox.backend.domain.catalog.PaginatedResult
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.model.Team
import ch.srgssr.pillarbox.backend.domain.model.User

/**
 * Reads and writes teams and their memberships.
 */
@SuppressWarnings("TooManyFunctions", "ComplexInterface")
interface TeamCatalog {
  /**
   * Finds a team by its identifier.
   *
   * @param id The unique identifier of the team.
   * @return The team, or `null` if it does not exist.
   */
  suspend fun find(id: String): Team?

  /**
   * Whether a team exists with this identifier.
   *
   * @param id The unique identifier of the team.
   * @return `true` if it exists, `false` otherwise.
   */
  suspend fun exists(id: String): Boolean

  /**
   * Persists or overwrites a team.
   *
   * @param item The team to save.
   * @return The persisted team.
   */
  suspend fun save(item: Team): Team

  /**
   * Deletes a team and, through cascade, its memberships.
   *
   * @param id The unique identifier of the team to delete.
   * @return `true` if the team existed and was deleted, `false` otherwise.
   */
  suspend fun delete(id: String): Boolean

  /**
   * Retrieves a page of teams, most recently updated first.
   *
   * @param text Optional filter on the team name; `null` or blank lists every team.
   * @param slice The window of the result to return.
   * @return A [PaginatedResult] with the matching teams and the total number of matches.
   */
  suspend fun page(
    text: String? = null,
    slice: QuerySlice = QuerySlice(),
  ): PaginatedResult<Team>

  /**
   * Retrieves the teams matching the given identifiers; unknown ids are absent.
   *
   * @param ids The team identifiers to look up.
   * @return The existing teams among [ids].
   */
  suspend fun list(ids: Collection<String>): List<Team>

  /**
   * Retrieves the teams a user is a member of.
   *
   * @param oidcSub The OIDC subject of the user.
   * @return The user's teams; empty if the user belongs to none.
   */
  suspend fun findTeamsOf(oidcSub: String): List<Team>

  /**
   * Retrieves the members of a team.
   *
   * @param teamId The team whose members are retrieved.
   * @param slice The window of the result to return.
   * @return The member users.
   */
  suspend fun findMembers(
    teamId: String,
    slice: QuerySlice = QuerySlice(limit = 100),
  ): List<User>

  /**
   * Adds a user to a team; adding an existing member again is a no-op.
   *
   * @param teamId The target team.
   * @param oidcSub The OIDC subject of the user to add.
   */
  suspend fun addMember(
    teamId: String,
    oidcSub: String,
  )

  /**
   * Removes a user from a team.
   *
   * @param teamId The team to remove the user from.
   * @param oidcSub The OIDC subject of the user to remove.
   * @return `true` if a membership was removed, `false` otherwise.
   */
  suspend fun removeMember(
    teamId: String,
    oidcSub: String,
  ): Boolean

  /**
   * Replaces the entire membership of a team in one operation.
   *
   * @param teamId The team whose members are replaced.
   * @param memberOidcSubs The OIDC subjects the team should have as members.
   */
  suspend fun replaceMembers(
    teamId: String,
    memberOidcSubs: Collection<String>,
  )

  /**
   * Counts the members of each of the given teams in a single lookup.
   *
   * @param teamIds The teams whose members are counted.
   * @return A map from team id to member count; memberless teams map to `0`.
   */
  suspend fun countMembersOf(vararg teamIds: String): Map<String, Long>
}
