package ch.srgssr.pillarbox.backend.adapter.web.api

import ch.srgssr.pillarbox.backend.adapter.web.api.dto.toSessionResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.toUserResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.http.toQuerySlice
import ch.srgssr.pillarbox.backend.adapter.web.http.withRole
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.port.SessionCatalog
import ch.srgssr.pillarbox.backend.domain.port.UserCatalog
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.getOrFail

/**
 * Configures the versioned user-related routes.
 *
 * Listing users is available to editors (e.g. to pick folder grant subjects);
 * session data is restricted to administrators.
 *
 * @param userCatalog Repository used to read user records.
 * @param sessionCatalog Repository used to read user sessions.
 */
fun Route.user(
  userCatalog: UserCatalog,
  sessionCatalog: SessionCatalog,
) {
  authenticate("pillarbox-jwt", "pillarbox-session") {
    route("v1/user") {
      withRole(Role.WRITE) {
        get {
          call.respond(
            userCatalog
              .page(slice = call.request.queryParameters.toQuerySlice())
              .items
              .map { it.toUserResponseV1() },
          )
        }

        get("/{id}") {
          val id = call.parameters.getOrFail("id")

          when (val user = userCatalog.find(id)?.toUserResponseV1()) {
            null -> call.respond(HttpStatusCode.NotFound)
            else -> call.respond(user)
          }
        }
      }

      withRole(Role.ADMIN) {
        get("/{id}/session") {
          val id = call.parameters.getOrFail("id")
          if (!userCatalog.exists(id)) return@get call.respond(HttpStatusCode.NotFound)

          call.respond(
            sessionCatalog
              .activeSessionsOf(id, call.request.queryParameters.toQuerySlice())
              .map { it.toSessionResponseV1() },
          )
        }
      }
    }
  }
}
