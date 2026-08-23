package ch.srgssr.pillarbox.backend.adapter.web.console

import ch.srgssr.pillarbox.backend.adapter.web.http.permissionChecker
import ch.srgssr.pillarbox.backend.adapter.web.http.toPageRequest
import ch.srgssr.pillarbox.backend.adapter.web.http.user
import ch.srgssr.pillarbox.backend.domain.catalog.MediaCriteria
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.port.FolderCatalog
import ch.srgssr.pillarbox.backend.domain.port.MediaCatalog
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.logger
import io.ktor.server.htmx.hx
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.ExperimentalKtorApi

private object MediaSearchRoute

private val logger = MediaSearchRoute.logger()

/**
 * Registers the HTMX fragment endpoint for the library search screen.
 *
 * A query swaps the library for cross-folder results ranked by relevance, each labelled with the
 * folder it belongs to; a blank query renders the regular library sections back.
 *
 * @param mediaCatalog Repository used to run the full-text search.
 * @param folderCatalog Repository used to resolve the folder of each result.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.mediaSearchFragments(
  mediaCatalog: MediaCatalog,
  folderCatalog: FolderCatalog,
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

      val result = mediaCatalog.page(MediaCriteria(text = query), QuerySlice(limit, offset))
      val mediaIds = result.items.map { it.id }
      val foldersByMedia = folderCatalog.findFoldersOf(mediaIds)

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
