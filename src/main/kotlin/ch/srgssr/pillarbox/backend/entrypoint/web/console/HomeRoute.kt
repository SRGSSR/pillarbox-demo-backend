package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.auth.withRole
import ch.srgssr.pillarbox.backend.domain.model.Folder
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.persistence.folder.FolderRepository
import ch.srgssr.pillarbox.backend.persistence.folder.FolderTable
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.htmx.hx
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.util.getOrFail
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull

private object HomeRoute

private val logger = HomeRoute.logger()

/**
 * Registers the console home page routes.
 *
 * @param mediaRepository Repository used to read and soft-delete media items.
 * @param folderRepository Repository used to read, create, and manage folder structure.
 */
fun Route.homePage(
  mediaRepository: MediaRepository,
  folderRepository: FolderRepository,
) {
  get {
    val folder =
      call.queryParameters["folderId"]
        ?.takeIf { it.isNotBlank() }
        ?.let { folderRepository.find(it) }
    logger.debug { "Fetching home page: folderId=${folder?.id}" }
    call.respondWithContext(
      "modules/home/home.page.peb",
      folder
        ?.let {
          mapOf(
            "folder" to folder,
            "ancestors" to folderRepository.findAncestors(folder.id).dropLast(1),
          )
        }.orEmpty(),
    )
  }

  get("bin") {
    logger.debug { "Fetching bin page" }
    call.respondWithContext(
      "modules/bin/bin.page.peb",
      mapOf("deleted" to true),
    )
  }

  folderFragments(folderRepository)
  mediaGridFragments(mediaRepository)

  withRole(Role.WRITE) {
    folderActions(mediaRepository, folderRepository)
    mediaGridActions(mediaRepository)
  }
}

/**
 * Registers HTMX fragment endpoints for the home page.
 *
 * @param folderRepository Repository used to fetch folders and their media counts.
 */
@OptIn(ExperimentalKtorApi::class)
private fun Route.folderFragments(folderRepository: FolderRepository) {
  hx.get("fragments/folder-grid") {
    val id = call.queryParameters["id"]?.takeIf { it.isNotBlank() }

    logger.debug { "Fetching folder grid: folderId=$id" }

    val subFolders =
      folderRepository
        .getAll(
          filter = { if (id == null) FolderTable.parentId.isNull() else FolderTable.parentId eq id },
        ).toList()

    val folderCounts = folderRepository.countMediaIn(*subFolders.map { it.id }.toTypedArray())

    call.respondWithContext(
      "modules/home/fragments/folder-grid.fragment.peb",
      mapOf(
        "folders" to subFolders,
        "folderCounts" to folderCounts,
      ),
    )
  }

  hx.get("fragments/folder-picker") {
    val mediaId = call.queryParameters.getOrFail("mediaId")
    val folder =
      call.queryParameters["folderId"]?.takeIf { it.isNotBlank() }?.let {
        folderRepository.find(it)
      }
    logger.debug { "Fetching folder picker: mediaId=$mediaId, folderId=${folder?.id}" }
    call.respondWithContext(
      "modules/home/fragments/folder-picker.fragment.peb",
      buildMap {
        put("mediaId", mediaId)
        folder?.let { put("folder", it) }
      },
    )
  }

  hx.get("fragments/folder-picker-child") {
    val id = call.queryParameters["id"]?.takeIf { it.isNotBlank() }
    val currentFolderId = call.queryParameters["currentFolderId"]?.takeIf { it.isNotBlank() }
    logger.debug { "Fetching folder picker children: parentId=$id, currentFolderId=$currentFolderId" }
    val subFolders =
      folderRepository
        .getAll(
          filter = { if (id == null) FolderTable.parentId.isNull() else FolderTable.parentId eq id },
        ).toList()
    val subFoldersCount = folderRepository.countSubfoldersOf(*subFolders.map { it.id }.toTypedArray())
    call.respondWithContext(
      "modules/home/fragments/folder-picker-child.fragment.peb",
      buildMap {
        put("subFoldersCount", subFoldersCount)
        put("folders", subFolders)
        currentFolderId?.let { put("currentFolderId", it) }
      },
    )
  }
}

/**
 * Registers HTMX action endpoints for the home page.
 *
 * @param mediaRepository Repository used to verify media existence.
 * @param folderRepository Repository used to persist folder and assignment changes.
 */
@OptIn(ExperimentalKtorApi::class)
@SuppressWarnings("LongMethod")
private fun Route.folderActions(
  mediaRepository: MediaRepository,
  folderRepository: FolderRepository,
) {
  hx.post("actions/folder") {
    val folder =
      call.receiveParameters().let { params ->
        Folder(
          name = params.getOrFail("name"),
          parentId = params["parentId"]?.takeIf { it.isNotBlank() },
        )
      }
    logger.info { "Creating folder: $folder" }
    call.respondWithContext(
      "modules/home/fragments/folder-card.fragment.peb",
      mapOf(
        "folder" to folderRepository.save(folder),
        "count" to 0,
      ),
    )
  }

  hx.patch("actions/folder/{id}") {
    val id = call.parameters.getOrFail("id")
    val name = call.receiveParameters().getOrFail("name")
    val folder = folderRepository.find(id) ?: return@patch call.respond(HttpStatusCode.NotFound)

    logger.info { "Renaming folder $id to '$name'" }
    call.respondWithContext(
      "modules/home/fragments/folder-card.fragment.peb",
      mapOf(
        "folder" to folderRepository.save(folder.copy(name = name)),
        "count" to folderRepository.countMediaIn(id),
      ),
    )
  }

  hx.delete("actions/folder/{id}") {
    val id = call.parameters.getOrFail("id")
    logger.info { "Deleting folder with ID: $id" }
    when (folderRepository.delete(id)) {
      true -> call.respond(HttpStatusCode.OK)
      false -> call.respond(HttpStatusCode.NotFound)
    }
  }

  hx.post("actions/folder/{id}/media") {
    val id = call.parameters.getOrFail("id")
    val mediaId = call.receiveParameters().getOrFail("mediaId")

    logger.info { "Assigning media $mediaId to folder $id" }

    val folder = folderRepository.find(id) ?: return@post call.respond(HttpStatusCode.NotFound)
    if (!mediaRepository.exists(mediaId)) return@post call.respond(HttpStatusCode.UnprocessableEntity)
    if (folderRepository.isMediaInFolder(id, mediaId)) return@post call.respond(HttpStatusCode.OK)

    folderRepository.assignMedia(id, mediaId)

    call.response.headers.append("HX-Retarget", "[id='media-card-$mediaId']")
    call.response.headers.append("HX-Reswap", "delete")
    call.respondWithContext(
      "modules/home/fragments/folder-card.fragment.peb",
      mapOf(
        "folder" to folder,
        "count" to folderRepository.countMediaIn(id),
        "oob" to true,
      ),
    )
  }

  hx.delete("actions/folder/{id}/media/{mediaId}") {
    val id = call.parameters.getOrFail("id")
    val mediaId = call.parameters.getOrFail("mediaId")

    val previousFolder = folderRepository.find(id) ?: return@delete call.respond(HttpStatusCode.NotFound)

    logger.info { "Removing folder assignment for media $mediaId" }

    when (folderRepository.removeMediaAssignment(id, mediaId)) {
      true -> {
        call.response.headers.append("HX-Retarget", "[id='media-card-$mediaId']")
        call.response.headers.append("HX-Reswap", "delete")
        call.respondWithContext(
          "modules/home/fragments/folder-card.fragment.peb",
          mapOf(
            "folder" to previousFolder,
            "count" to folderRepository.countMediaIn(previousFolder.id),
            "oob" to true,
          ),
        )
      }

      false -> {
        call.respond(HttpStatusCode.NotFound)
      }
    }
  }
}
