package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.auth.user
import ch.srgssr.pillarbox.backend.authz.permissionChecker
import ch.srgssr.pillarbox.backend.authz.withMediaWrite
import ch.srgssr.pillarbox.backend.db.PaginatedResult
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.Role
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
      val query = call.parameters["q"]?.takeIf { it.isNotBlank() }

      logger.debug { "Fetching media grid: pageRequest=$this, folderId=$folderId, deleted=$deleted, query=$query" }

      val result =
        when {
          query != null -> mediaRepository.search(query, limit, offset, filter = { MediaTable.deleted eq deleted })
          deleted -> mediaRepository.getAllPaginated(limit, offset, filter = { MediaTable.deleted eq true })
          folderId != null -> mediaRepository.findMediaInFolder(folderId, limit, offset)
          else -> mediaRepository.findMediaWithoutFolder(limit, offset)
        }

      // The bin is admin-only and a folder grid shares one ACL, so a single verdict fits both; a
      // cross-folder search spans folders and must resolve write access per media item.
      val writableByMedia =
        when {
          deleted -> result.writeAccess(call.user.hasAnyRole(setOf(Role.ADMIN)))
          query != null -> call.permissionChecker.canWriteMedia(call.user, result.items.map { it.id })
          else -> result.writeAccess(call.permissionChecker.canWriteFolder(call.user, folderId))
        }

      call.respondWithContext(
        "shared/fragments/media-grid.fragment.peb",
        buildMap {
          put("result", result)
          put("nextPage", nextPage)
          put("deleted", deleted)
          put("writableByMedia", writableByMedia)
          query?.let { put("query", it) }
          folderId?.let { put("folderId", it) }
        },
      )
    }
  }
}

/**
 * Maps every media item on the page to the same write [verdict].
 *
 * @param verdict Whether the current user may write all items on the page.
 * @return A map from media id to [verdict].
 */
private fun PaginatedResult<Media>.writeAccess(verdict: Boolean): Map<String, Boolean> =
  items.associate { it.id to verdict }

/**
 * Registers HTMX action endpoints for the paginated media-grid fragment.
 *
 * @param mediaRepository Repository used to apply soft-delete and restore operations.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.mediaGridActions(mediaRepository: MediaRepository) {
  hx.delete("actions/media/{id}") {
    val id = call.parameters.getOrFail("id")
    withMediaWrite(id) {
      logger.info { "Attempting to delete media with ID: $id" }
      when (mediaRepository.softDelete(id)) {
        true -> call.respond(HttpStatusCode.OK)
        false -> call.respond(HttpStatusCode.NotFound)
      }
    }
  }
}

/**
 * Registers HTMX admin action endpoints for the paginated media-grid fragment.
 *
 * @param mediaRepository Repository used to apply soft-delete and restore operations.
 */
fun Route.mediaGridAdminActions(mediaRepository: MediaRepository) {
  hx.post("actions/media/{id}/restore") {
    val id = call.parameters.getOrFail("id")

    logger.info { "Attempting to restore media with ID: $id" }
    when (mediaRepository.restore(id)) {
      true -> call.respond(HttpStatusCode.OK)
      false -> call.respond(HttpStatusCode.NotFound)
    }
  }
}
