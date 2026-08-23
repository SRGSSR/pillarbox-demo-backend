package ch.srgssr.pillarbox.backend.adapter.web.api

import ch.srgssr.pillarbox.backend.adapter.web.api.dto.AssignMediaRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderPermissionRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.FolderRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.toFolderPermissionResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.toFolderResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.toMediaResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.http.AuthenticatedUserPlugin
import ch.srgssr.pillarbox.backend.adapter.web.http.toQuerySlice
import ch.srgssr.pillarbox.backend.adapter.web.http.withFolderWrite
import ch.srgssr.pillarbox.backend.adapter.web.http.withMediaWrite
import ch.srgssr.pillarbox.backend.adapter.web.http.withRole
import ch.srgssr.pillarbox.backend.domain.catalog.FolderScope
import ch.srgssr.pillarbox.backend.domain.catalog.MediaCriteria
import ch.srgssr.pillarbox.backend.domain.catalog.map
import ch.srgssr.pillarbox.backend.domain.model.PermissionSubject
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.port.FolderCatalog
import ch.srgssr.pillarbox.backend.domain.port.FolderGrants
import ch.srgssr.pillarbox.backend.domain.port.MediaCatalog
import ch.srgssr.pillarbox.backend.domain.port.TeamCatalog
import ch.srgssr.pillarbox.backend.domain.port.UserCatalog
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.util.getOrFail

/**
 * Configures the versioned folder-related routes.
 *
 * Mutations are guarded by `withFolderWrite`/`withMediaWrite`: restricted folders only accept
 * changes from granted editors and administrators. Folder grants themselves are managed by
 * whoever can write the folder.
 *
 * @param folderCatalog Repository used to manage folder persistence.
 * @param mediaCatalog Repository used to manage media persistence.
 * @param folderGrants Repository used to manage folder grants.
 * @param userCatalog Repository used to validate user references in grants.
 * @param teamCatalog Repository used to validate team references in grants.
 */
@SuppressWarnings("LongMethod", "CyclomaticComplexMethod")
fun Route.folder(
  folderCatalog: FolderCatalog,
  mediaCatalog: MediaCatalog,
  folderGrants: FolderGrants,
  userCatalog: UserCatalog,
  teamCatalog: TeamCatalog,
) {
  authenticate("pillarbox-jwt", "pillarbox-session") {
    route("v1/folder") {
      install(AuthenticatedUserPlugin)

      get {
        val scope =
          call.request.queryParameters["parentId"]?.let { FolderScope.In(it) }
            ?: FolderScope.Anywhere

        call.respond(
          folderCatalog
            .list(scope, call.request.queryParameters.toQuerySlice())
            .map { it.toFolderResponseV1() },
        )
      }

      get("/{id}") {
        val id = call.parameters.getOrFail("id")

        when (val folder = folderCatalog.find(id)?.toFolderResponseV1()) {
          null -> call.respond(HttpStatusCode.NotFound)
          else -> call.respond(folder)
        }
      }

      get("/{id}/media") {
        val id = call.parameters.getOrFail("id")
        if (!folderCatalog.exists(id)) return@get call.respond(HttpStatusCode.NotFound)

        val slice = call.request.queryParameters.toQuerySlice()
        call.respond(
          mediaCatalog
            .page(MediaCriteria(scope = FolderScope.In(id)), slice)
            .map { it.toMediaResponseV1() }
            .items,
        )
      }

      withRole(Role.WRITE) {
        post {
          val folder = call.receive<FolderRequestV1>().toFolder()
          withFolderWrite(folder.parentId) {
            call.respond(
              HttpStatusCode.Created,
              folderCatalog.save(folder).toFolderResponseV1(),
            )
          }
        }

        patch("/{id}") {
          val id = call.parameters.getOrFail("id")
          val existing = folderCatalog.find(id) ?: return@patch call.respond(HttpStatusCode.NotFound)

          val folder = call.receive<FolderRequestV1>().toFolder().copy(id = id)
          // Moving additionally requires write access to the new parent.
          val moved = folder.parentId != existing.parentId
          withFolderWrite(id, folder.parentId.takeIf { moved }) {
            call.respond(
              HttpStatusCode.Created,
              folderCatalog.save(folder).toFolderResponseV1(),
            )
          }
        }

        delete("/{id}") {
          val id = call.parameters.getOrFail("id")
          withFolderWrite(id) {
            when (folderCatalog.delete(id)) {
              true -> call.respond(HttpStatusCode.NoContent)
              false -> call.respond(HttpStatusCode.NotFound)
            }
          }
        }

        post("/{id}/media") {
          val id = call.parameters.getOrFail("id")
          if (!folderCatalog.exists(id)) return@post call.respond(HttpStatusCode.NotFound, "Folder not found")

          val request = call.receive<AssignMediaRequestV1>()
          if (!mediaCatalog.exists(request.mediaId)) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, "Referenced Media id does not exist")
          }

          withFolderWrite(id) {
            withMediaWrite(request.mediaId) {
              folderCatalog.assignMedia(id, request.mediaId)
              call.respond(HttpStatusCode.Created)
            }
          }
        }

        delete("/{id}/media/{mediaId}") {
          val id = call.parameters.getOrFail("id")
          val mediaId = call.parameters.getOrFail("mediaId")
          withFolderWrite(id) {
            when (folderCatalog.removeMediaAssignment(id, mediaId)) {
              true -> call.respond(HttpStatusCode.NoContent)
              false -> call.respond(HttpStatusCode.NotFound)
            }
          }
        }

        get("/{id}/permission") {
          val id = call.parameters.getOrFail("id")
          if (!folderCatalog.exists(id)) return@get call.respond(HttpStatusCode.NotFound)

          withFolderWrite(id) {
            call.respond(
              folderGrants
                .findGrantsInChain(id)
                .map { it.toFolderPermissionResponseV1() },
            )
          }
        }

        post("/{id}/permission") {
          val id = call.parameters.getOrFail("id")
          if (!folderCatalog.exists(id)) return@post call.respond(HttpStatusCode.NotFound)

          withFolderWrite(id) {
            val permission =
              call.receive<FolderPermissionRequestV1>().toFolderPermission(id)
            if (permission == null) {
              call.respond(HttpStatusCode.BadRequest, "Exactly one of oidcSub, teamId or role must be provided")
              return@withFolderWrite
            }

            when (val subject = permission.subject) {
              is PermissionSubject.ForUser -> {
                if (!userCatalog.exists(subject.oidcSub)) {
                  call.respond(HttpStatusCode.UnprocessableEntity, "Referenced user does not exist")
                  return@withFolderWrite
                }
              }

              is PermissionSubject.ForTeam -> {
                if (!teamCatalog.exists(subject.teamId)) {
                  call.respond(HttpStatusCode.UnprocessableEntity, "Referenced team does not exist")
                  return@withFolderWrite
                }
              }

              is PermissionSubject.ForRole -> {
                Unit
              }
            }

            call.respond(
              HttpStatusCode.Created,
              folderGrants.save(permission).toFolderPermissionResponseV1(),
            )
          }
        }

        delete("/{id}/permission/{permissionId}") {
          val id = call.parameters.getOrFail("id")
          val permissionId = call.parameters.getOrFail("permissionId")

          withFolderWrite(id) {
            val permission = folderGrants.find(permissionId)
            if (permission == null || permission.folderId != id) {
              call.respond(HttpStatusCode.NotFound)
              return@withFolderWrite
            }

            folderGrants.delete(permissionId)
            call.respond(HttpStatusCode.NoContent)
          }
        }
      }
    }
  }
}
