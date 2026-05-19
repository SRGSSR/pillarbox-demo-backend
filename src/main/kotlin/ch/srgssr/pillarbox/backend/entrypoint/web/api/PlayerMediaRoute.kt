package ch.srgssr.pillarbox.backend.entrypoint.web.api

import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toPlayerResponse
import ch.srgssr.pillarbox.backend.entrypoint.web.service.MediaSourceSelector
import ch.srgssr.pillarbox.backend.entrypoint.web.service.toDrmPreferences
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.toQuerySlice
import ch.srgssr.pillarbox.backend.io.parseHeaderList
import ch.srgssr.pillarbox.backend.io.parseParamList
import ch.srgssr.pillarbox.backend.persistence.folder.FolderRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.getOrFail
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

/**
 * Configures the versioned public player media routes under `v1/player/`.
 *
 * These endpoints return player-optimized responses with media sources selected
 * according to the client's stream-type and DRM preferences.
 *
 * @param mediaRepository The repository used to manage media entities.
 * @param folderRepository The repository used to verify folder existence.
 */
fun Route.playerMedia(
  mediaRepository: MediaRepository,
  folderRepository: FolderRepository,
) {
  route("v1/player/") {
    route("media") {
      get {
        with(call.request.queryParameters.toQuerySlice()) {
          val mediaSourceSelector = call.request.toMediaSourceSelector()
          call.respond(
            mediaRepository
              .getAll(
                limit,
                offset,
                filter = { MediaTable.deleted eq false },
              ).map { it.toPlayerResponse(mediaSourceSelector) }
              .toList(),
          )
        }
      }

      get("/{id}") {
        val id = call.parameters.getOrFail("id")

        val media =
          mediaRepository.findOne {
            (MediaTable.id eq id) and (MediaTable.deleted eq false)
          } ?: return@get call.respond(HttpStatusCode.NotFound)

        call.respond(media.toPlayerResponse(call.request.toMediaSourceSelector()))
      }
    }

    route("folder") {
      get("/{id}/media") {
        val id = call.parameters.getOrFail("id")
        if (!folderRepository.exists(id)) return@get call.respond(HttpStatusCode.NotFound)

        with(call.request.queryParameters.toQuerySlice()) {
          val mediaSourceSelector = call.request.toMediaSourceSelector()
          call.respond(
            mediaRepository
              .findMediaInFolder(
                folderId = id,
                limit,
                offset,
                filter = { MediaTable.deleted eq false },
              ).items
              .map { it.toPlayerResponse(mediaSourceSelector) },
          )
        }
      }
    }
  }
}

/**
 * Builds a [MediaSourceSelector] from the current request's stream-type and DRM preferences.
 *
 * @return A [MediaSourceSelector] configured with the client's preferences.
 */
private fun ApplicationRequest.toMediaSourceSelector() =
  MediaSourceSelector(
    toMimeTypePreferences(),
    toDrmPreferences(),
  )

/**
 * Extracts the client's preferred MIME types from the `stream-type` query parameter,
 * falling back to the `X-Accept-Stream-Type` header if the parameter is absent or empty.
 *
 * @return An ordered list of preferred stream types.
 */
private fun ApplicationRequest.toMimeTypePreferences() =
  queryParameters
    .parseParamList("stream-type")
    .ifEmpty { call.request.headers.parseHeaderList("X-Accept-Stream-Type") }

/**
 * Extracts the client's DRM preferences from the `drm` query parameter,
 * falling back to the `X-Accept-DRM` header if the parameter is absent or empty.
 *
 * @return list of parsed [DrmPreference][ch.srgssr.pillarbox.backend.entrypoint.web.service.DrmPreference]s,
 *         preserving priority order.
 */
private fun ApplicationRequest.toDrmPreferences() =
  call.request.queryParameters
    .parseParamList("drm")
    .ifEmpty { call.request.headers.parseHeaderList("X-Accept-DRM") }
    .toDrmPreferences()
