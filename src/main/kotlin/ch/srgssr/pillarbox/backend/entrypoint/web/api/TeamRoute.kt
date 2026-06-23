package ch.srgssr.pillarbox.backend.entrypoint.web.api

import ch.srgssr.pillarbox.backend.auth.withRole
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.AddTeamMemberRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.TeamRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toTeamResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toUserResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.toQuerySlice
import ch.srgssr.pillarbox.backend.persistence.team.TeamRepository
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.util.getOrFail
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

/**
 * Configures the versioned team-related routes.
 *
 * Listing teams and members is available to editors (e.g. to pick folder grant
 * subjects); creating and deleting teams and managing membership is restricted
 * to administrators.
 *
 * @param teamRepository Repository used to manage team persistence.
 * @param userRepository Repository used to validate team member references.
 */
@SuppressWarnings("LongMethod")
fun Route.team(
  teamRepository: TeamRepository,
  userRepository: UserRepository,
) {
  authenticate("pillarbox-jwt", "pillarbox-session") {
    route("v1/team") {
      withRole(Role.WRITE) {
        get {
          with(call.request.queryParameters.toQuerySlice()) {
            call.respond(
              teamRepository
                .getAll(limit, offset)
                .map { it.toTeamResponseV1() }
                .toList(),
            )
          }
        }

        get("/{id}") {
          val id = call.parameters.getOrFail("id")

          when (val team = teamRepository.find(id)?.toTeamResponseV1()) {
            null -> call.respond(HttpStatusCode.NotFound)
            else -> call.respond(team)
          }
        }

        get("/{id}/member") {
          val id = call.parameters.getOrFail("id")
          if (!teamRepository.exists(id)) return@get call.respond(HttpStatusCode.NotFound)

          with(call.request.queryParameters.toQuerySlice()) {
            call.respond(
              teamRepository
                .findMembers(id, limit, offset)
                .map { it.toUserResponseV1() },
            )
          }
        }
      }

      withRole(Role.ADMIN) {
        post {
          val team = call.receive<TeamRequestV1>().toTeam()
          call.respond(
            HttpStatusCode.Created,
            teamRepository.save(team).toTeamResponseV1(),
          )
        }

        delete("/{id}") {
          val id = call.parameters.getOrFail("id")
          when (teamRepository.delete(id)) {
            true -> call.respond(HttpStatusCode.NoContent)
            false -> call.respond(HttpStatusCode.NotFound)
          }
        }

        post("/{id}/member") {
          val id = call.parameters.getOrFail("id")
          if (!teamRepository.exists(id)) return@post call.respond(HttpStatusCode.NotFound, "Team not found")

          with(call.receive<AddTeamMemberRequestV1>()) {
            if (!userRepository.exists(oidcSub)) {
              return@post call.respond(
                HttpStatusCode.UnprocessableEntity,
                "Referenced user does not exist",
              )
            }

            teamRepository.addMember(id, oidcSub)
            call.respond(HttpStatusCode.Created)
          }
        }

        delete("/{id}/member/{oidcSub}") {
          val id = call.parameters.getOrFail("id")
          val oidcSub = call.parameters.getOrFail("oidcSub")

          when (teamRepository.removeMember(id, oidcSub)) {
            true -> call.respond(HttpStatusCode.NoContent)
            false -> call.respond(HttpStatusCode.NotFound)
          }
        }
      }
    }
  }
}
