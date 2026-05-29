package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.auth.withRole
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.log.trace
import ch.srgssr.pillarbox.backend.persistence.folder.FolderRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.htmx.hx
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.util.getOrFail
import io.ktor.utils.io.ExperimentalKtorApi

private object ConsoleEditorRoute

private val logger = ConsoleEditorRoute.logger()

/**
 * Registers the media editor page routes.
 *
 * @param mediaRepository Repository used to look up and persist media items.
 * @param folderRepository Repository used to look up folders and assign media to them.
 */
@OptIn(ExperimentalKtorApi::class)
@SuppressWarnings("LongMethod", "CyclomaticComplexMethod")
fun Route.editorPage(
  mediaRepository: MediaRepository,
  folderRepository: FolderRepository,
) {
  withRole(Role.WRITE) {
    get("editor/{id?}") {
      val media =
        call.parameters["id"]
          ?.let { mediaRepository.find(it) }
          ?.also { logger.debug { "Opening editor for media: $it" } }
      val folder = call.queryParameters["folderId"]?.takeIf { it.isNotBlank() }?.let { folderRepository.find(it) }
      call.respondWithContext(
        "modules/editor/editor.page.peb",
        buildMap {
          media?.let { put("item", media) }
          folder?.let {
            put("folderId", it.id)
            put("folder", it)
            put("ancestors", folderRepository.findAncestors(it.id).dropLast(1))
          }
        },
      )
    }

    get("editor/{id}/duplicate") {
      val id = call.parameters.getOrFail("id")
      val media =
        mediaRepository
          .find(id)
          ?.also { logger.debug { "Opening editor with duplicate data from original ID: $id" } }
          ?: return@get call.respond(HttpStatusCode.NotFound)
      val folder = call.queryParameters["folderId"]?.takeIf { it.isNotBlank() }?.let { folderRepository.find(it) }
      call.respondWithContext(
        "modules/editor/editor.page.peb",
        buildMap {
          put("item", media.copy(id = ""))
          folder?.let {
            put("folderId", it.id)
            put("folder", it)
            put("ancestors", folderRepository.findAncestors(it.id).dropLast(1))
          }
        },
      )
    }

    hx.get("/fragments/editor/{fragment}") {
      val fragment =
        EditorFragment.find(call.parameters["fragment"])
          ?: return@get call.respond(HttpStatusCode.NotFound)
      val index = call.request.queryParameters["index"]?.toInt() ?: 0
      val sourceIndex = call.request.queryParameters["sourceIndex"]?.toInt()
      logger.trace { "Rendering fragment: ${fragment.name} at index $index" }
      call.respondWithContext(
        fragment.template,
        buildMap {
          put("index", index)
          sourceIndex?.let { put("sourceIndex", it) }
        },
      )
    }

    hx.post("actions/media") {
      val request = call.receive<Media>()
      val folderId = call.parameters["folderId"]?.takeIf { it.isNotBlank() }
      logger.info { "Saving media: ${request.id}, folderId=$folderId" }
      val media = mediaRepository.save(request)
      folderId?.let { folderRepository.assignMedia(it, media.id) }

      val redirect = if (folderId != null) "/console?folderId=$folderId" else "/console"
      call.response.headers.append("HX-Redirect", redirect)
      call.respond(HttpStatusCode.OK)
    }
  }
}

/**
 * Represents the various dynamic UI components (fragments) that can be
 * injected into the Media Editor via HTMX.
 *
 * @property id The string identifier used in the URL path.
 */
private enum class EditorFragment(
  val id: String,
) {
  CHAPTER("chapter"),
  TIME_RANGE("time-range"),
  SOURCE("source"),
  SUBTITLE("subtitle"),
  DRM("drm"),
  ;

  /**
   * The resolved classpath to the Pebble template for this fragment.
   */
  val template: String
    get() = "modules/editor/fragments/$id-row.fragment.peb"

  companion object {
    /**
     * Matches a URL path parameter to an [EditorFragment].
     *
     * @param value The raw string from the {fragment} path parameter.
     *
     * @return The matching fragment, or null if the ID is not recognized.
     */
    fun find(value: String?): EditorFragment? = entries.find { it.id == value }
  }
}
