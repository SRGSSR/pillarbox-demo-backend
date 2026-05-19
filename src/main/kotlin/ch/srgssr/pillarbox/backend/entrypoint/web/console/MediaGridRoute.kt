package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.entrypoint.web.utils.toPageRequest
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.htmx.hx
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.util.getOrFail
import io.ktor.utils.io.ExperimentalKtorApi
import org.jetbrains.exposed.v1.core.eq

private object ConsoleMediaRoute

private val logger = ConsoleMediaRoute.logger()

/**
 * Registers the HTMX fragment endpoint for the paginated media grid.
 *
 * @param mediaRepository Repository used to fetch paginated media items.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.mediaGridFragments(mediaRepository: MediaRepository) {
  hx.get("fragments/media-grid") {
    with(call.queryParameters.toPageRequest()) {
      val deleted = call.parameters["deleted"] == "true"
      val folderId = call.parameters["folderId"]?.takeIf { it.isNotBlank() }

      logger.debug { "Fetching media grid: pageRequest=$this, folderId=$folderId, deleted=$deleted" }

      val result =
        when {
          deleted -> mediaRepository.getAllPaginated(limit, offset, filter = { MediaTable.deleted eq true })
          folderId != null -> mediaRepository.findMediaInFolder(folderId, limit, offset)
          else -> mediaRepository.findMediaWithoutFolder(limit, offset)
        }

      call.respondWithContext(
        "shared/fragments/media-grid.fragment.peb",
        buildMap {
          put("result", result)
          put("nextPage", nextPage)
          put("deleted", deleted)
          folderId?.let { put("folderId", folderId) }
        },
      )
    }
  }
}

/**
 * Registers HTMX action endpoints for the paginated media-grid fragment.
 *
 * @param mediaRepository Repository used to apply soft-delete and restore operations.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.mediaGridActions(mediaRepository: MediaRepository) {
  hx.delete("actions/media/{id}") {
    val id = call.parameters.getOrFail("id")
    logger.info { "Attempting to delete media with ID: $id" }
    when (mediaRepository.softDelete(id)) {
      true -> call.respond(HttpStatusCode.OK)
      false -> call.respond(HttpStatusCode.NotFound)
    }
  }

  hx.post("actions/media/{id}/restore") {
    val id = call.parameters.getOrFail("id")
    logger.info { "Attempting to restore media with ID: $id" }
    when (mediaRepository.restore(id)) {
      true -> call.respond(HttpStatusCode.OK)
      false -> call.respond(HttpStatusCode.NotFound)
    }
  }
}
