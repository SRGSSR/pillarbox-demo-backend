package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.auth.withRole
import ch.srgssr.pillarbox.backend.authz.withFolderWrite
import ch.srgssr.pillarbox.backend.authz.withMediaWrite
import ch.srgssr.pillarbox.backend.domain.model.Folder
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.integrationlayer.IntegrationLayerClient
import ch.srgssr.pillarbox.backend.integrationlayer.toMedia
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
import io.ktor.server.routing.RoutingContext
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
 * @param integrationLayerClient Client used to import media metadata by URN.
 */
@OptIn(ExperimentalKtorApi::class)
@SuppressWarnings("LongMethod", "CyclomaticComplexMethod")
fun Route.editorPage(
  mediaRepository: MediaRepository,
  folderRepository: FolderRepository,
  integrationLayerClient: IntegrationLayerClient,
) {
  withRole(Role.WRITE) {
    get("editor/{id?}") {
      val mediaId = call.parameters["id"]
      val folder = folderParam(folderRepository)

      val renderEditor: suspend () -> Unit = {
        val media = mediaId?.let { mediaRepository.find(it) }?.also { logger.debug { "Opening editor for media: $it" } }
        respondEditor(folderRepository, media, exists = media != null, folder = folder)
      }

      // Editing targets the media's folder; creating new media targets the destination folder.
      if (mediaId != null) {
        withMediaWrite(mediaId, renderEditor)
      } else {
        withFolderWrite(folder?.id, block = renderEditor)
      }
    }

    get("editor/{id}/duplicate") {
      val id = call.parameters.getOrFail("id")
      val media =
        mediaRepository
          .find(id)
          ?.also { logger.debug { "Opening editor with duplicate data from original ID: $id" } }
          ?: return@get call.respond(HttpStatusCode.NotFound)
      val folder = folderParam(folderRepository)

      // Duplicating creates a new media in the destination folder.
      withFolderWrite(folder?.id) {
        respondEditor(folderRepository, media.copy(id = ""), exists = false, folder = folder)
      }
    }

    get("editor/import") {
      val urn =
        call.queryParameters["urn"]?.takeIf { it.isNotBlank() } ?: return@get call.respond(HttpStatusCode.BadRequest)

      // Overwriting an existing media is governed by its folder; the destination folder gates the import.
      val mediaFolder = folderRepository.findFolderOf(urn)
      val folder = folderParam(folderRepository) ?: mediaFolder

      withFolderWrite(*setOf(mediaFolder?.id, folder?.id).toTypedArray()) {
        val media =
          runCatching { integrationLayerClient.findMediaComposition(urn) }
            .onFailure { logger.info { "Failed to fetch media composition for URN $urn: ${it.message}" } }
            .getOrNull()
            ?.toMedia() ?: return@get call.respond(HttpStatusCode.BadGateway)

        logger.debug { "Opening editor with imported data for URN: $urn" }

        respondEditor(folderRepository, media, exists = mediaRepository.exists(media.id), folder = folder)
      }
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

      // Overwriting an existing media is governed by its folder; the destination folder gates the save.
      withMediaWrite(request.id.takeIf { mediaRepository.exists(it) }) {
        withFolderWrite(folderId) {
          logger.info { "Saving media: ${request.id}, folderId=$folderId" }
          val media = mediaRepository.save(request)
          folderId?.let { folderRepository.assignMedia(it, media.id) }

          val redirect = if (folderId != null) "/console?folderId=$folderId" else "/console"
          call.response.headers.append("HX-Redirect", redirect)
          call.respond(HttpStatusCode.OK)
        }
      }
    }
  }
}

/**
 * Resolves the folder referenced by the `folderId` query parameter.
 *
 * @param folderRepository Repository used to look up the folder.
 * @return The folder, or null when the parameter is absent, blank, or unknown.
 */
private suspend fun RoutingContext.folderParam(folderRepository: FolderRepository): Folder? =
  call.queryParameters["folderId"]?.takeIf { it.isNotBlank() }?.let { folderRepository.find(it) }

/**
 * Renders the media editor page for the given item and folder context.
 *
 * @param folderRepository Repository used to resolve the folder's ancestors.
 * @param media The media item to edit, or null for a blank editor.
 * @param exists Whether the item is already persisted, switching the editor to edit mode.
 * @param folder The folder the editor operates in, or null for the root scope.
 */
private suspend fun RoutingContext.respondEditor(
  folderRepository: FolderRepository,
  media: Media?,
  exists: Boolean,
  folder: Folder?,
) {
  call.respondWithContext(
    "modules/editor/editor.page.peb",
    buildMap {
      media?.let { put("item", it) }
      put("exists", exists)
      folder?.let {
        put("folderId", it.id)
        put("folder", it)
        put("ancestors", folderRepository.findAncestors(it.id).dropLast(1))
      }
    },
  )
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
