package ch.srgssr.pillarbox.backend.adapter.web.api

import ch.srgssr.pillarbox.backend.adapter.web.api.dto.AddTeamMemberRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.TeamRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.toTeamResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.toUserResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.http.toQuerySlice
import ch.srgssr.pillarbox.backend.adapter.web.http.withRole
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.port.TeamCatalog
import ch.srgssr.pillarbox.backend.domain.port.UserCatalog
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

/**
 * Configures the versioned team-related routes.
 *
 * Listing teams and members is available to editors (e.g. to pick folder grant
 * subjects); creating and deleting teams and managing membership is restricted
 * to administrators.
 *
 * @param teamCatalog Repository used to manage team persistence.
 * @param userCatalog Repository used to validate team member references.
 */
@SuppressWarnings("LongMethod")
fun Route.team(
  teamCatalog: TeamCatalog,
  userCatalog: UserCatalog,
) {
  authenticate("pillarbox-jwt", "pillarbox-session") {
    route("v1/team") {
      withRole(Role.WRITE) {
        get {
          call.respond(
            teamCatalog
              .page(slice = call.request.queryParameters.toQuerySlice())
              .items
              .map { it.toTeamResponseV1() },
          )
        }

        get("/{id}") {
          val id = call.parameters.getOrFail("id")

          when (val team = teamCatalog.find(id)?.toTeamResponseV1()) {
            null -> call.respond(HttpStatusCode.NotFound)
            else -> call.respond(team)
          }
        }

        get("/{id}/member") {
          val id = call.parameters.getOrFail("id")
          if (!teamCatalog.exists(id)) return@get call.respond(HttpStatusCode.NotFound)

          call.respond(
            teamCatalog
              .findMembers(id, call.request.queryParameters.toQuerySlice())
              .map { it.toUserResponseV1() },
          )
        }
      }

      withRole(Role.ADMIN) {
        post {
          val team = call.receive<TeamRequestV1>().toTeam()
          call.respond(
            HttpStatusCode.Created,
            teamCatalog.save(team).toTeamResponseV1(),
          )
        }

        delete("/{id}") {
          val id = call.parameters.getOrFail("id")
          when (teamCatalog.delete(id)) {
            true -> call.respond(HttpStatusCode.NoContent)
            false -> call.respond(HttpStatusCode.NotFound)
          }
        }

        post("/{id}/member") {
          val id = call.parameters.getOrFail("id")
          if (!teamCatalog.exists(id)) return@post call.respond(HttpStatusCode.NotFound, "Team not found")

          with(call.receive<AddTeamMemberRequestV1>()) {
            if (!userCatalog.exists(oidcSub)) {
              return@post call.respond(
                HttpStatusCode.UnprocessableEntity,
                "Referenced user does not exist",
              )
            }

            teamCatalog.addMember(id, oidcSub)
            call.respond(HttpStatusCode.Created)
          }
        }

        delete("/{id}/member/{oidcSub}") {
          val id = call.parameters.getOrFail("id")
          val oidcSub = call.parameters.getOrFail("oidcSub")

          when (teamCatalog.removeMember(id, oidcSub)) {
            true -> call.respond(HttpStatusCode.NoContent)
            false -> call.respond(HttpStatusCode.NotFound)
          }
        }
      }
    }
  }
}
