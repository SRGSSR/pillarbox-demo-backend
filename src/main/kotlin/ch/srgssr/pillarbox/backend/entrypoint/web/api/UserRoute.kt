package ch.srgssr.pillarbox.backend.entrypoint.web.api

import ch.srgssr.pillarbox.backend.auth.withRole
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toSessionResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toUserResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.toQuerySlice
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
import ch.srgssr.pillarbox.backend.persistence.session.SessionTable
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.getOrFail
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import kotlin.time.Clock

/**
 * Configures the versioned user-related routes, restricted to administrators.
 *
 * @param userRepository Repository used to read user records.
 * @param sessionRepository Repository used to read user sessions.
 */
fun Route.user(
  userRepository: UserRepository,
  sessionRepository: SessionRepository,
) {
  authenticate("pillarbox-jwt", "pillarbox-session") {
    withRole(Role.ADMIN) {
      route("v1/user") {
        get {
          with(call.request.queryParameters.toQuerySlice()) {
            call.respond(
              userRepository
                .getAll(limit, offset)
                .map { it.toUserResponseV1() }
                .toList(),
            )
          }
        }

        get("/{id}") {
          val id = call.parameters.getOrFail("id")

          when (val user = userRepository.find(id)?.toUserResponseV1()) {
            null -> call.respond(HttpStatusCode.NotFound)
            else -> call.respond(user)
          }
        }

        get("/{id}/session") {
          val id = call.parameters.getOrFail("id")
          if (!userRepository.exists(id)) return@get call.respond(HttpStatusCode.NotFound)

          val activeUserSessions = {
            (SessionTable.oidcSub eq id) and
              (SessionTable.expiresAt greater Clock.System.now().toUtcOffsetDateTime())
          }

          with(call.request.queryParameters.toQuerySlice()) {
            call.respond(
              sessionRepository
                // Sessions are re-stamped with a fresh expiry on every update,
                // so this orders most recently updated first.
                .getAll(limit, offset, activeUserSessions, listOf(SessionTable.expiresAt to SortOrder.DESC))
                .map { it.toSessionResponseV1() }
                .toList(),
            )
          }
        }
      }
    }
  }
}
