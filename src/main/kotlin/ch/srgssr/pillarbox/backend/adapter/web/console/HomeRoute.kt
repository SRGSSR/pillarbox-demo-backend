package ch.srgssr.pillarbox.backend.adapter.web.console

import ch.srgssr.pillarbox.backend.adapter.web.http.permissionChecker
import ch.srgssr.pillarbox.backend.adapter.web.http.user
import ch.srgssr.pillarbox.backend.adapter.web.http.withFolderWrite
import ch.srgssr.pillarbox.backend.adapter.web.http.withMediaWrite
import ch.srgssr.pillarbox.backend.adapter.web.http.withRole
import ch.srgssr.pillarbox.backend.domain.catalog.FolderScope
import ch.srgssr.pillarbox.backend.domain.model.Folder
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.port.FolderCatalog
import ch.srgssr.pillarbox.backend.domain.port.FolderGrants
import ch.srgssr.pillarbox.backend.domain.port.MediaCatalog
import ch.srgssr.pillarbox.backend.domain.port.TeamCatalog
import ch.srgssr.pillarbox.backend.domain.port.UserCatalog
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
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

private object HomeRoute

private val logger = HomeRoute.logger()

/**
 * Registers the console home page routes.
 *
 * @param mediaCatalog Repository used to read and soft-delete media items.
 * @param folderCatalog Repository used to read, create, and manage folder structure.
 * @param folderGrants Repository used to read and manage folder grants.
 * @param userCatalog Repository used to resolve and search users for grants.
 * @param teamCatalog Repository used to resolve and search teams for grants.
 */
fun Route.homePage(
  mediaCatalog: MediaCatalog,
  folderCatalog: FolderCatalog,
  folderGrants: FolderGrants,
  userCatalog: UserCatalog,
  teamCatalog: TeamCatalog,
) {
  get {
    val folder =
      call.queryParameters["folderId"]
        ?.takeIf { it.isNotBlank() }
        ?.let { folderCatalog.find(it) }
    logger.debug { "Fetching home page: folderId=${folder?.id}" }
    call.respondWithContext(
      "modules/home/home.page.peb",
      buildMap {
        put("section", "library")
        put("canWrite", call.permissionChecker.canWriteFolder(call.user, folder?.id))
        folder?.let {
          put("folder", it)
          put("ancestors", folderCatalog.findAncestors(it.id).dropLast(1))
        }
      },
    )
  }

  get("bin") {
    logger.debug { "Fetching bin page" }
    call.respondWithContext(
      "modules/bin/bin.page.peb",
      mapOf("deleted" to true),
    )
  }

  folderFragments(folderCatalog)
  mediaGridFragments(mediaCatalog)
  mediaSearchFragments(mediaCatalog, folderCatalog)

  withRole(Role.WRITE) {
    folderActions(mediaCatalog, folderCatalog)
    mediaGridActions(mediaCatalog)
    folderPermissionFragments(folderCatalog, folderGrants, userCatalog, teamCatalog)
    folderPermissionActions(folderCatalog, folderGrants, userCatalog, teamCatalog)
  }

  withRole(Role.ADMIN) {
    mediaGridAdminActions(mediaCatalog)
  }
}

/**
 * Registers HTMX fragment endpoints for the home page.
 *
 * @param folderCatalog Repository used to fetch folders and their media counts.
 */
@OptIn(ExperimentalKtorApi::class)
private fun Route.folderFragments(folderCatalog: FolderCatalog) {
  hx.get("fragments/folder-grid") {
    val id = call.queryParameters["id"]?.takeIf { it.isNotBlank() }

    logger.debug { "Fetching folder grid: folderId=$id" }

    val subFolders =
      folderCatalog.list(scope = id?.let { FolderScope.In(it) } ?: FolderScope.Unassigned)

    val folderCounts = folderCatalog.countMediaIn(*subFolders.map { it.id }.toTypedArray())

    call.respondWithContext(
      "modules/home/fragments/folder-grid.fragment.peb",
      mapOf(
        "folders" to subFolders,
        "folderCounts" to folderCounts,
        "folderWriteAccess" to call.permissionChecker.canWriteFolders(call.user, subFolders),
      ),
    )
  }

  hx.get("fragments/folder-picker") {
    val mediaId = call.queryParameters.getOrFail("mediaId")
    val folder =
      call.queryParameters["folderId"]?.takeIf { it.isNotBlank() }?.let {
        folderCatalog.find(it)
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
      folderCatalog.list(scope = id?.let { FolderScope.In(it) } ?: FolderScope.Unassigned)
    val subFoldersCount = folderCatalog.countSubfoldersOf(*subFolders.map { it.id }.toTypedArray())
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
 * @param mediaCatalog Repository used to verify media existence.
 * @param folderCatalog Repository used to persist folder and assignment changes.
 */
@OptIn(ExperimentalKtorApi::class)
@SuppressWarnings("LongMethod", "CyclomaticComplexMethod")
private fun Route.folderActions(
  mediaCatalog: MediaCatalog,
  folderCatalog: FolderCatalog,
) {
  hx.post("actions/folder") {
    val folder =
      call.receiveParameters().let { params ->
        Folder(
          name = params.getOrFail("name"),
          parentId = params["parentId"]?.takeIf { it.isNotBlank() },
        )
      }
    withFolderWrite(folder.parentId) {
      logger.info { "Creating folder: $folder" }
      call.respondWithContext(
        "modules/home/fragments/folder-card.fragment.peb",
        mapOf(
          "folder" to folderCatalog.save(folder),
          "count" to 0,
        ),
      )
    }
  }

  hx.patch("actions/folder/{id}") {
    val id = call.parameters.getOrFail("id")
    val name = call.receiveParameters().getOrFail("name")
    val folder = folderCatalog.find(id) ?: return@patch call.respond(HttpStatusCode.NotFound)
    withFolderWrite(id) {
      logger.info { "Renaming folder $id to '$name'" }
      call.respondWithContext(
        "modules/home/fragments/folder-card.fragment.peb",
        mapOf(
          "folder" to folderCatalog.save(folder.copy(name = name)),
          "count" to folderCatalog.countMediaIn(id),
        ),
      )
    }
  }

  hx.delete("actions/folder/{id}") {
    val id = call.parameters.getOrFail("id")
    withFolderWrite(id) {
      logger.info { "Deleting folder with ID: $id" }
      when (folderCatalog.delete(id)) {
        true -> call.respond(HttpStatusCode.OK)
        false -> call.respond(HttpStatusCode.NotFound)
      }
    }
  }

  hx.post("actions/folder/{id}/media") {
    val id = call.parameters.getOrFail("id")
    val mediaId = call.receiveParameters().getOrFail("mediaId")

    logger.info { "Assigning media $mediaId to folder $id" }

    val folder = folderCatalog.find(id) ?: return@post call.respond(HttpStatusCode.NotFound)
    if (!mediaCatalog.exists(mediaId)) return@post call.respond(HttpStatusCode.UnprocessableEntity)
    if (folderCatalog.isMediaInFolder(id, mediaId)) return@post call.respond(HttpStatusCode.OK)

    withFolderWrite(id) {
      withMediaWrite(mediaId) {
        folderCatalog.assignMedia(id, mediaId)

        call.response.headers.append("HX-Retarget", "[id='media-card-$mediaId']")
        call.response.headers.append("HX-Reswap", "delete")
        call.respondWithContext(
          "modules/home/fragments/folder-card.fragment.peb",
          mapOf(
            "folder" to folder,
            "count" to folderCatalog.countMediaIn(id),
            "oob" to true,
          ),
        )
      }
    }
  }

  hx.delete("actions/folder/{id}/media/{mediaId}") {
    val id = call.parameters.getOrFail("id")
    val mediaId = call.parameters.getOrFail("mediaId")

    val previousFolder = folderCatalog.find(id) ?: return@delete call.respond(HttpStatusCode.NotFound)
    withFolderWrite(id) {
      logger.info { "Removing folder assignment for media $mediaId" }

      when (folderCatalog.removeMediaAssignment(id, mediaId)) {
        true -> {
          call.response.headers.append("HX-Retarget", "[id='media-card-$mediaId']")
          call.response.headers.append("HX-Reswap", "delete")
          call.respondWithContext(
            "modules/home/fragments/folder-card.fragment.peb",
            mapOf(
              "folder" to previousFolder,
              "count" to folderCatalog.countMediaIn(previousFolder.id),
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
}
