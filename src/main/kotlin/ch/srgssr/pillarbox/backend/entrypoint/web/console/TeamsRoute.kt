package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.auth.withRole
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.toPageRequest
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.persistence.team.TeamRepository
import ch.srgssr.pillarbox.backend.persistence.team.TeamTable
import io.ktor.server.htmx.hx
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.ExperimentalKtorApi
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase

private object ConsoleTeamRoute

private val logger = ConsoleTeamRoute.logger()

/**
 * Registers the console teams page and its paginated table fragment, both restricted to users with
 * write access.
 *
 * @param teamRepository Repository used to read, search and count the members of teams.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.teamsPage(teamRepository: TeamRepository) {
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
}
