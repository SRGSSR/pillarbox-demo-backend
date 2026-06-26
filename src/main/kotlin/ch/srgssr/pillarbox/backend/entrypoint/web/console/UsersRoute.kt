package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.auth.withRole
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.toPageRequest
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import ch.srgssr.pillarbox.backend.persistence.user.UserTable
import io.ktor.server.htmx.hx
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.ExperimentalKtorApi
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase

private object ConsoleUserRoute

private val logger = ConsoleUserRoute.logger()

/**
 * Registers the console users page and its paginated table fragment, both restricted to users with
 * write access.
 *
 * @param userRepository Repository used to read and search users.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.usersPage(userRepository: UserRepository) {
  withRole(Role.WRITE) {
    get("users") {
      logger.debug { "Fetching users page" }
      call.respondWithContext("modules/users/users.page.peb", mapOf("section" to "users"))
    }

    hx.get("fragments/user-table") {
      val query = call.queryParameters["q"].orEmpty().trim()
      with(call.queryParameters.toPageRequest()) {
        val term = query.takeIf { it.isNotBlank() }?.lowercase()
        logger.debug { "Fetching user table: pageRequest=$this, q='$query'" }
        val result =
          userRepository.getAllPaginated(
            limit = limit,
            offset = offset,
            filter = term?.let { { UserTable.displayName.lowerCase() like "%$it%" } },
            sort = listOf(UserTable.updatedAt to SortOrder.DESC),
          )
        call.respondWithContext(
          "modules/users/fragments/user-table.fragment.peb",
          mapOf("result" to result, "nextPage" to nextPage, "q" to query),
        )
      }
    }
  }
}
