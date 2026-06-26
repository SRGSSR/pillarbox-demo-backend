package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.auth.withRole
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.model.Team
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.toPageRequest
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.persistence.team.TeamRepository
import ch.srgssr.pillarbox.backend.persistence.team.TeamTable
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import ch.srgssr.pillarbox.backend.persistence.user.UserTable
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.htmx.hx
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.util.getOrFail
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase

private object ConsoleTeamRoute

private val logger = ConsoleTeamRoute.logger()

/**
 * Registers the console teams page and its fragments. The directory is visible to users with write
 * access; creating teams and managing their members is reserved for administrators.
 *
 * @param teamRepository Repository used to read, search, count and create teams.
 * @param userRepository Repository used to search and resolve users when picking members.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.teamsPage(
  teamRepository: TeamRepository,
  userRepository: UserRepository,
) {
  withRole(Role.WRITE) {
    get("teams") {
      logger.debug { "Fetching teams page" }
      call.respondWithContext("modules/teams/teams.page.peb", mapOf("section" to "teams"))
    }

    hx.get("fragments/team-table") {
      val query = call.queryParameters["q"].orEmpty().trim()
      with(call.queryParameters.toPageRequest()) {
        val term = query.takeIf { it.isNotBlank() }?.lowercase()
        logger.debug { "Fetching team table: pageRequest=$this, q='$query'" }
        val result =
          teamRepository.getAllPaginated(
            limit = limit,
            offset = offset,
            filter = term?.let { { TeamTable.name.lowerCase() like "%$it%" } },
            sort = listOf(TeamTable.updatedAt to SortOrder.DESC),
          )
        val memberCounts = teamRepository.countMembersOf(*result.items.map { it.id }.toTypedArray())
        call.respondWithContext(
          "modules/teams/fragments/team-table.fragment.peb",
          mapOf("result" to result, "memberCounts" to memberCounts, "nextPage" to nextPage, "q" to query),
        )
      }
    }
  }

  withRole(Role.ADMIN) {
    teamFormFragments(teamRepository, userRepository)
    teamActions(teamRepository, userRepository)
  }
}

/**
 * Registers the admin-only fragments backing the team dialog: the form itself (empty for a new team
 * or populated for an existing one) and the member picker.
 *
 * @param teamRepository Repository used to resolve a team and its members.
 * @param userRepository Repository used to search and resolve member users.
 */
@OptIn(ExperimentalKtorApi::class)
private fun Route.teamFormFragments(
  teamRepository: TeamRepository,
  userRepository: UserRepository,
) {
  hx.get("fragments/team-form") {
    val teamId =
      call.queryParameters["teamId"] ?: return@get call.respondWithContext(
        "modules/teams/fragments/team-form.fragment.peb",
        mapOf("members" to emptyList<Any>()),
      )

    val team = teamRepository.find(teamId) ?: return@get call.respond(HttpStatusCode.NotFound)
    call.respondWithContext(
      "modules/teams/fragments/team-form.fragment.peb",
      mapOf("team" to team, "members" to teamRepository.findMembers(teamId)),
    )
  }

  hx.get("fragments/member-options") {
    val query = call.queryParameters["q"].orEmpty().trim()
    call.respondWithContext(
      "shared/fragments/datalist-options.fragment.peb",
      mapOf("options" to searchUsers(query, userRepository)),
    )
  }

  hx.get("fragments/member-row") {
    val oidcSub = call.queryParameters.getOrFail("oidcSub")
    val member = userRepository.find(oidcSub) ?: return@get call.respond(HttpStatusCode.NotFound)
    call.respondWithContext("modules/teams/fragments/member-row.fragment.peb", mapOf("member" to member))
  }
}

/**
 * Registers the admin-only actions that create and update a team together with its members.
 *
 * @param teamRepository Repository used to persist teams.
 * @param userRepository Repository used to resolve the submitted members.
 */
@OptIn(ExperimentalKtorApi::class)
private fun Route.teamActions(
  teamRepository: TeamRepository,
  userRepository: UserRepository,
) {
  hx.post("actions/team") {
    val params = call.receiveParameters()
    val name = params["name"]?.trim().orEmpty()
    if (name.isBlank()) {
      return@post call.respond(HttpStatusCode.UnprocessableEntity, "Enter a team name")
    }

    val members = resolveMembers(params, userRepository)
    val team = teamRepository.save(Team(name = name))
    teamRepository.replaceMembers(team.id, members)
    logger.info { "Created team ${team.id} ('$name') with ${members.size} member(s)" }
    call.respondTeamRow(team, members.size)
  }

  hx.post("actions/team/{id}") {
    val id = call.parameters.getOrFail("id")
    val existing = teamRepository.find(id) ?: return@post call.respond(HttpStatusCode.NotFound)
    val params = call.receiveParameters()
    val name = params["name"]?.trim().orEmpty()
    if (name.isBlank()) {
      return@post call.respond(HttpStatusCode.UnprocessableEntity, "Enter a team name")
    }

    val members = resolveMembers(params, userRepository)
    val updated = teamRepository.save(existing.copy(name = name))
    teamRepository.replaceMembers(updated.id, members)
    logger.info { "Updated team ${updated.id} ('$name') with ${members.size} member(s)" }
    call.respondTeamRow(updated, members.size)
  }
}

/** Responds with the table row for [team], carrying its [memberCount]. */
private suspend fun ApplicationCall.respondTeamRow(
  team: Team,
  memberCount: Int,
) = respondWithContext(
  "modules/teams/fragments/team-row.fragment.peb",
  mapOf("team" to team, "memberCount" to memberCount.toLong()),
)

/**
 * Resolves the submitted `memberId` values to the OIDC subs of existing users, dropping unknown ids.
 *
 * @param params The submitted form parameters.
 * @param userRepository Repository used to confirm the members exist.
 * @return The OIDC subs of the existing members, without blanks or duplicates.
 */
private suspend fun resolveMembers(
  params: Parameters,
  userRepository: UserRepository,
): List<String> {
  val requested =
    params
      .getAll("memberId")
      .orEmpty()
      .filter { it.isNotBlank() }
      .distinct()
  return if (requested.isEmpty()) {
    emptyList()
  } else {
    userRepository
      .getAll(limit = requested.size, filter = { UserTable.oidcSub inList requested })
      .toList()
      .map { it.oidcSub }
  }
}

/**
 * Searches users by display name for the member picker.
 *
 * @param query The text typed into the search box; blank lists the most recently updated users.
 * @param userRepository Repository used to search users.
 * @param limit The maximum number of suggestions to return.
 * @return Up to [limit] options, each carrying the display name and the user's OIDC sub as its id.
 */
private suspend fun searchUsers(
  query: String,
  userRepository: UserRepository,
  limit: Int = 20,
): List<DatalistOption> {
  val term = query.takeIf { it.isNotBlank() }?.lowercase()
  return userRepository
    .getAll(
      limit = limit,
      filter = term?.let { { UserTable.displayName.lowerCase() like "%$it%" } },
      sort = listOf(UserTable.updatedAt to SortOrder.DESC),
    ).toList()
    .map { DatalistOption(it.displayName, "User", it.oidcSub) }
}
