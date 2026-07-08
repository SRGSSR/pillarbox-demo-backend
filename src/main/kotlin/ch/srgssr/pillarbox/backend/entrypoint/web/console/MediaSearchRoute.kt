package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.auth.user
import ch.srgssr.pillarbox.backend.authz.permissionChecker
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.toPageRequest
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.persistence.folder.FolderRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaTable
import io.ktor.server.htmx.hx
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.ExperimentalKtorApi
import org.jetbrains.exposed.v1.core.eq

private object MediaSearchRoute

private val logger = MediaSearchRoute.logger()

/**
 * Registers the HTMX fragment endpoint for the library search screen.
 *
 * A query swaps the library for cross-folder results ranked by relevance, each labelled with the
 * folder it belongs to; a blank query renders the regular library sections back.
 *
 * @param mediaRepository Repository used to run the full-text search.
 * @param folderRepository Repository used to resolve the folder of each result.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.mediaSearchFragments(
  mediaRepository: MediaRepository,
  folderRepository: FolderRepository,
) {
  hx.get("fragments/media-search") {
    with(call.queryParameters.toPageRequest()) {
      val folderId = call.parameters["folderId"]?.takeIf { it.isNotBlank() }

      logger.debug { "Fetching media search: pageRequest=$this, folderId=$folderId, query=${call.parameters["q"]}" }

      val query =
        call.parameters["q"]?.takeIf { it.isNotBlank() }
          ?: return@get call.respondWithContext(
            "modules/home/fragments/library-sections.fragment.peb",
            buildMap { folderId?.let { put("folderId", it) } },
          )

      val result = mediaRepository.search(query, limit, offset, filter = { MediaTable.deleted eq false })
      val mediaIds = result.items.map { it.id }
      val foldersByMedia = folderRepository.findFoldersOf(mediaIds)

      call.respondWithContext(
        "modules/home/fragments/media-search-results.fragment.peb",
        mapOf(
          "result" to result,
          "nextPage" to nextPage,
          "query" to query,
          "writableByMedia" to call.permissionChecker.canWriteMedia(call.user, mediaIds, foldersByMedia),
          "foldersByMedia" to foldersByMedia,
        ),
      )
    }
  }
}
