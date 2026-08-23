package ch.srgssr.pillarbox.backend.adapter.web.console

import ch.srgssr.pillarbox.backend.adapter.web.http.toPageRequest
import ch.srgssr.pillarbox.backend.adapter.web.http.withRole
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.port.UserCatalog
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.logger
import io.ktor.server.htmx.hx
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.ExperimentalKtorApi

private object ConsoleUserRoute

private val logger = ConsoleUserRoute.logger()

/**
 * Registers the console users page and its paginated table fragment, both restricted to users with
 * write access.
 *
 * @param userCatalog Repository used to read and search users.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.usersPage(userCatalog: UserCatalog) {
  withRole(Role.WRITE) {
    get("users") {
      logger.debug { "Fetching users page" }
      call.respondWithContext("modules/users/users.page.peb", mapOf("section" to "users"))
    }

    hx.get("fragments/user-table") {
      val query = call.queryParameters["q"].orEmpty().trim()
      with(call.queryParameters.toPageRequest()) {
        logger.debug { "Fetching user table: pageRequest=$this, q='$query'" }
        val result = userCatalog.page(query, QuerySlice(limit, offset))
        call.respondWithContext(
          "modules/users/fragments/user-table.fragment.peb",
          mapOf("result" to result, "nextPage" to nextPage, "q" to query),
        )
      }
    }
  }
}
