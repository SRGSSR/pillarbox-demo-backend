package ch.srgssr.pillarbox.backend.entrypoint.web.api

import ch.srgssr.pillarbox.backend.auth.withRole
import ch.srgssr.pillarbox.backend.db.map
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.AssignMediaRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.FolderRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toFolderResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toMediaResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.toQuerySlice
import ch.srgssr.pillarbox.backend.persistence.folder.FolderRepository
import ch.srgssr.pillarbox.backend.persistence.folder.FolderTable
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaTable
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
 * @param folderRepository Repository used to manage folder persistence.
 * @param mediaRepository Repository used to manage media persistence.
 */
@SuppressWarnings("LongMethod", "CyclomaticComplexMethod")
fun Route.folder(
  folderRepository: FolderRepository,
  mediaRepository: MediaRepository,
) {
  authenticate("pillarbox-jwt", "pillarbox-session") {
    route("v1/folder") {
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
                filter = { MediaTable.deleted eq false },
              ).map { it.toMediaResponseV1() }
              .items
              .toList(),
          )
        }
      }

      withRole(Role.WRITE) {
        post {
          val folder = call.receive<FolderRequestV1>().toFolder()
          call.respond(
            HttpStatusCode.Created,
            folderRepository.save(folder).toFolderResponseV1(),
          )
        }

        patch("/{id}") {
          val id = call.parameters.getOrFail("id")
          if (!folderRepository.exists(id)) return@patch call.respond(HttpStatusCode.NotFound)

          val folder = call.receive<FolderRequestV1>().toFolder().copy(id = id)
          call.respond(
            HttpStatusCode.Created,
            folderRepository.save(folder).toFolderResponseV1(),
          )
        }

        delete("/{id}") {
          val id = call.parameters.getOrFail("id")
          when (folderRepository.delete(id)) {
            true -> call.respond(HttpStatusCode.NoContent)
            false -> call.respond(HttpStatusCode.NotFound)
          }
        }

        post("/{id}/media") {
          val id = call.parameters.getOrFail("id")
          if (!folderRepository.exists(id)) return@post call.respond(HttpStatusCode.NotFound, "Folder not found")

          with(call.receive<AssignMediaRequestV1>()) {
            if (!mediaRepository.exists(mediaId)) {
              return@post call.respond(
                HttpStatusCode.UnprocessableEntity,
                "Referenced Media id does not exist",
              )
            }

            folderRepository.assignMedia(id, mediaId)
            call.respond(HttpStatusCode.Created)
          }
        }

        delete("/{id}/media/{mediaId}") {
          val id = call.parameters.getOrFail("id")
          val mediaId = call.parameters.getOrFail("mediaId")

          when (folderRepository.removeMediaAssignment(id, mediaId)) {
            true -> call.respond(HttpStatusCode.NoContent)
            false -> call.respond(HttpStatusCode.NotFound)
          }
        }
      }
    }
  }
}
