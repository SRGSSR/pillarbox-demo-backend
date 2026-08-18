package ch.srgssr.pillarbox.backend.entrypoint.web.api

import ch.srgssr.pillarbox.backend.auth.AuthenticatedUserPlugin
import ch.srgssr.pillarbox.backend.auth.withRole
import ch.srgssr.pillarbox.backend.authz.withFolderWrite
import ch.srgssr.pillarbox.backend.authz.withMediaWrite
import ch.srgssr.pillarbox.backend.db.map
import ch.srgssr.pillarbox.backend.domain.model.PermissionSubject
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.AssignMediaRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.FolderPermissionRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.FolderRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toFolderPermissionResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toFolderResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toMediaResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.toQuerySlice
import ch.srgssr.pillarbox.backend.persistence.folder.FolderPermissionRepository
import ch.srgssr.pillarbox.backend.persistence.folder.FolderRepository
import ch.srgssr.pillarbox.backend.persistence.folder.FolderTable
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaVisibility
import ch.srgssr.pillarbox.backend.persistence.team.TeamRepository
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.eq

/**
 * Configures the versioned folder-related routes.
 *
 * Mutations are guarded by `withFolderWrite`/`withMediaWrite`: restricted folders only accept
 * changes from granted editors and administrators. Folder grants themselves are managed by
 * whoever can write the folder.
 *
 * @param folderRepository Repository used to manage folder persistence.
 * @param mediaRepository Repository used to manage media persistence.
 * @param folderPermissionRepository Repository used to manage folder grants.
 * @param userRepository Repository used to validate user references in grants.
 * @param teamRepository Repository used to validate team references in grants.
 */
@SuppressWarnings("LongMethod", "CyclomaticComplexMethod")
fun Route.folder(
  folderRepository: FolderRepository,
  mediaRepository: MediaRepository,
  folderPermissionRepository: FolderPermissionRepository,
  userRepository: UserRepository,
  teamRepository: TeamRepository,
) {
  authenticate("pillarbox-jwt", "pillarbox-session") {
    route("v1/folder") {
      install(AuthenticatedUserPlugin)

      get {
        val filter =
          call.request.queryParameters["parentId"]?.let {
            { FolderTable.parentId eq it }
          }

        with(call.request.queryParameters.toQuerySlice()) {
          call.respond(
            folderRepository
              .getAll(
                limit,
                offset,
                filter,
              ).map { it.toFolderResponseV1() }
              .toList(),
          )
        }
      }

      get("/{id}") {
        val id = call.parameters.getOrFail("id")

        when (val folder = folderRepository.find(id)?.toFolderResponseV1()) {
          null -> call.respond(HttpStatusCode.NotFound)
          else -> call.respond(folder)
        }
      }

      get("/{id}/media") {
        val id = call.parameters.getOrFail("id")
        if (!folderRepository.exists(id)) return@get call.respond(HttpStatusCode.NotFound)

        with(call.request.queryParameters.toQuerySlice()) {
          call.respond(
            mediaRepository
              .findMediaInFolder(
                folderId = id,
                limit,
                offset,
                filter = { MediaVisibility.ACTIVE },
              ).map { it.toMediaResponseV1() }
              .items
              .toList(),
          )
        }
      }

      withRole(Role.WRITE) {
        post {
          val folder = call.receive<FolderRequestV1>().toFolder()
          withFolderWrite(folder.parentId) {
            call.respond(
              HttpStatusCode.Created,
              folderRepository.save(folder).toFolderResponseV1(),
            )
          }
        }

        patch("/{id}") {
          val id = call.parameters.getOrFail("id")
          val existing = folderRepository.find(id) ?: return@patch call.respond(HttpStatusCode.NotFound)

          val folder = call.receive<FolderRequestV1>().toFolder().copy(id = id)
          // Moving additionally requires write access to the new parent.
          val moved = folder.parentId != existing.parentId
          withFolderWrite(id, folder.parentId.takeIf { moved }) {
            call.respond(
              HttpStatusCode.Created,
              folderRepository.save(folder).toFolderResponseV1(),
            )
          }
        }

        delete("/{id}") {
          val id = call.parameters.getOrFail("id")
          withFolderWrite(id) {
            when (folderRepository.delete(id)) {
              true -> call.respond(HttpStatusCode.NoContent)
              false -> call.respond(HttpStatusCode.NotFound)
            }
          }
        }

        post("/{id}/media") {
          val id = call.parameters.getOrFail("id")
          if (!folderRepository.exists(id)) return@post call.respond(HttpStatusCode.NotFound, "Folder not found")

          val request = call.receive<AssignMediaRequestV1>()
          if (!mediaRepository.exists(request.mediaId)) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, "Referenced Media id does not exist")
          }

          withFolderWrite(id) {
            withMediaWrite(request.mediaId) {
              folderRepository.assignMedia(id, request.mediaId)
              call.respond(HttpStatusCode.Created)
            }
          }
        }

        delete("/{id}/media/{mediaId}") {
          val id = call.parameters.getOrFail("id")
          val mediaId = call.parameters.getOrFail("mediaId")
          withFolderWrite(id) {
            when (folderRepository.removeMediaAssignment(id, mediaId)) {
              true -> call.respond(HttpStatusCode.NoContent)
              false -> call.respond(HttpStatusCode.NotFound)
            }
          }
        }

        get("/{id}/permission") {
          val id = call.parameters.getOrFail("id")
          if (!folderRepository.exists(id)) return@get call.respond(HttpStatusCode.NotFound)

          withFolderWrite(id) {
            call.respond(
              folderPermissionRepository
                .findGrantsInChain(id)
                .map { it.toFolderPermissionResponseV1() },
            )
          }
        }

        post("/{id}/permission") {
          val id = call.parameters.getOrFail("id")
          if (!folderRepository.exists(id)) return@post call.respond(HttpStatusCode.NotFound)

          withFolderWrite(id) {
            val permission =
              call.receive<FolderPermissionRequestV1>().toFolderPermission(id)
            if (permission == null) {
              call.respond(HttpStatusCode.BadRequest, "Exactly one of oidcSub, teamId or role must be provided")
              return@withFolderWrite
            }

            when (val subject = permission.subject) {
              is PermissionSubject.ForUser -> {
                if (!userRepository.exists(subject.oidcSub)) {
                  call.respond(HttpStatusCode.UnprocessableEntity, "Referenced user does not exist")
                  return@withFolderWrite
                }
              }

              is PermissionSubject.ForTeam -> {
                if (!teamRepository.exists(subject.teamId)) {
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
              folderPermissionRepository.save(permission).toFolderPermissionResponseV1(),
            )
          }
        }

        delete("/{id}/permission/{permissionId}") {
          val id = call.parameters.getOrFail("id")
          val permissionId = call.parameters.getOrFail("permissionId")

          withFolderWrite(id) {
            val permission = folderPermissionRepository.find(permissionId)
            if (permission == null || permission.folderId != id) {
              call.respond(HttpStatusCode.NotFound)
              return@withFolderWrite
            }

            folderPermissionRepository.delete(permissionId)
            call.respond(HttpStatusCode.NoContent)
          }
        }
      }
    }
  }
}
