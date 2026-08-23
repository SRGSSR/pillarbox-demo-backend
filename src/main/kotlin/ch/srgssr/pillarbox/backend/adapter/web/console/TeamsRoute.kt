package ch.srgssr.pillarbox.backend.adapter.web.console

import ch.srgssr.pillarbox.backend.adapter.web.http.toPageRequest
import ch.srgssr.pillarbox.backend.adapter.web.http.withRole
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.model.Team
import ch.srgssr.pillarbox.backend.domain.port.TeamCatalog
import ch.srgssr.pillarbox.backend.domain.port.UserCatalog
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
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

private object ConsoleTeamRoute

private val logger = ConsoleTeamRoute.logger()

/**
 * Registers the console teams page and its fragments. The directory is visible to users with write
 * access; creating teams and managing their members is reserved for administrators.
 *
 * @param teamCatalog Repository used to read, search, count and create teams.
 * @param userCatalog Repository used to search and resolve users when picking members.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.teamsPage(
  teamCatalog: TeamCatalog,
  userCatalog: UserCatalog,
) {
  withRole(Role.WRITE) {
    get("teams") {
      logger.debug { "Fetching teams page" }
      call.respondWithContext("modules/teams/teams.page.peb", mapOf("section" to "teams"))
    }

    hx.get("fragments/team-table") {
      val query = call.queryParameters["q"].orEmpty().trim()
      with(call.queryParameters.toPageRequest()) {
        logger.debug { "Fetching team table: pageRequest=$this, q='$query'" }
        val result = teamCatalog.page(query, QuerySlice(limit, offset))
        val memberCounts = teamCatalog.countMembersOf(*result.items.map { it.id }.toTypedArray())
        call.respondWithContext(
          "modules/teams/fragments/team-table.fragment.peb",
          mapOf("result" to result, "memberCounts" to memberCounts, "nextPage" to nextPage, "q" to query),
        )
      }
    }
  }

  withRole(Role.ADMIN) {
    teamFormFragments(teamCatalog, userCatalog)
    teamActions(teamCatalog, userCatalog)
  }
}

/**
 * Registers the admin-only fragments backing the team dialog: the form itself (empty for a new team
 * or populated for an existing one) and the member picker.
 *
 * @param teamCatalog Repository used to resolve a team and its members.
 * @param userCatalog Repository used to search and resolve member users.
 */
@OptIn(ExperimentalKtorApi::class)
private fun Route.teamFormFragments(
  teamCatalog: TeamCatalog,
  userCatalog: UserCatalog,
) {
  hx.get("fragments/team-form") {
    val teamId =
      call.queryParameters["teamId"] ?: return@get call.respondWithContext(
        "modules/teams/fragments/team-form.fragment.peb",
        mapOf("members" to emptyList<Any>()),
      )

    val team = teamCatalog.find(teamId) ?: return@get call.respond(HttpStatusCode.NotFound)
    call.respondWithContext(
      "modules/teams/fragments/team-form.fragment.peb",
      mapOf("team" to team, "members" to teamCatalog.findMembers(teamId)),
    )
  }

  hx.get("fragments/member-options") {
    val query = call.queryParameters["q"].orEmpty().trim()
    call.respondWithContext(
      "shared/fragments/datalist-options.fragment.peb",
      mapOf("options" to searchUsers(query, userCatalog)),
    )
  }

  hx.get("fragments/member-row") {
    val oidcSub = call.queryParameters.getOrFail("oidcSub")
    val member = userCatalog.find(oidcSub) ?: return@get call.respond(HttpStatusCode.NotFound)
    call.respondWithContext("modules/teams/fragments/member-row.fragment.peb", mapOf("member" to member))
  }
}

/**
 * Registers the admin-only actions that create and update a team together with its members.
 *
 * @param teamCatalog Repository used to persist teams.
 * @param userCatalog Repository used to resolve the submitted members.
 */
@OptIn(ExperimentalKtorApi::class)
private fun Route.teamActions(
  teamCatalog: TeamCatalog,
  userCatalog: UserCatalog,
) {
  hx.post("actions/team") {
    val params = call.receiveParameters()
    val name = params["name"]?.trim().orEmpty()
    if (name.isBlank()) {
      return@post call.respond(HttpStatusCode.UnprocessableEntity, "Enter a team name")
    }

    val members = resolveMembers(params, userCatalog)
    val team = teamCatalog.save(Team(name = name))
    teamCatalog.replaceMembers(team.id, members)
    logger.info { "Created team ${team.id} ('$name') with ${members.size} member(s)" }
    call.respondTeamRow(team, members.size)
  }

  hx.post("actions/team/{id}") {
    val id = call.parameters.getOrFail("id")
    val existing = teamCatalog.find(id) ?: return@post call.respond(HttpStatusCode.NotFound)
    val params = call.receiveParameters()
    val name = params["name"]?.trim().orEmpty()
    if (name.isBlank()) {
      return@post call.respond(HttpStatusCode.UnprocessableEntity, "Enter a team name")
    }

    val members = resolveMembers(params, userCatalog)
    val updated = teamCatalog.save(existing.copy(name = name))
    teamCatalog.replaceMembers(updated.id, members)
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
 * @param userCatalog Repository used to confirm the members exist.
 * @return The OIDC subs of the existing members, without blanks or duplicates.
 */
private suspend fun resolveMembers(
  params: Parameters,
  userCatalog: UserCatalog,
): List<String> {
  val requested =
    params
      .getAll("memberId")
      .orEmpty()
      .filter { it.isNotBlank() }
      .distinct()
  return userCatalog.list(requested).map { it.oidcSub }
}

/**
 * Searches users by display name for the member picker.
 *
 * @param query The text typed into the search box; blank lists the most recently updated users.
 * @param userCatalog Repository used to search users.
 * @param limit The maximum number of suggestions to return.
 * @return Up to [limit] options, each carrying the display name and the user's OIDC sub as its id.
 */
private suspend fun searchUsers(
  query: String,
  userCatalog: UserCatalog,
  limit: Int = 20,
): List<DatalistOption> =
  userCatalog
    .page(query, QuerySlice(limit = limit))
    .items
    .map { DatalistOption(it.displayName, "User", it.oidcSub) }
