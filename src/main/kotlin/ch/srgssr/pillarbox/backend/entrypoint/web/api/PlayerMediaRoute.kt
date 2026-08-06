package ch.srgssr.pillarbox.backend.entrypoint.web.api

import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toPlayerResponse
import ch.srgssr.pillarbox.backend.entrypoint.web.service.MediaPreferences
import ch.srgssr.pillarbox.backend.entrypoint.web.service.MediaSourceSelector
import ch.srgssr.pillarbox.backend.entrypoint.web.service.toDrmPreferences
import ch.srgssr.pillarbox.backend.entrypoint.web.service.toPlatformPreferences
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
        if (!call.request.hasKnownPlatform()) return@get call.respond(HttpStatusCode.BadRequest, "Unknown platform")

        with(call.request.queryParameters.toQuerySlice()) {
          val mediaSourceSelector = call.request.toMediaSourceSelector()
          val query = call.request.queryParameters["q"]
          call.respond(
            mediaRepository
              .findActiveMedia(query, limit, offset)
              .map { it.toPlayerResponse(mediaSourceSelector) },
          )
        }
      }

      get("/{id}") {
        if (!call.request.hasKnownPlatform()) return@get call.respond(HttpStatusCode.BadRequest, "Unknown platform")

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
        if (!call.request.hasKnownPlatform()) return@get call.respond(HttpStatusCode.BadRequest, "Unknown platform")

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
  this.toMediaPreferences().let {
    MediaSourceSelector(
      it.mimeTypePreferences,
      it.drmPreferences,
    )
  }

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

/**
 * Extracts the client's target platform from the `platform` query parameter,
 * falling back to the `X-Target-Platform` header if the parameter is absent or blank.
 *
 * @return The raw platform identifier, or `null` if the client targets no platform.
 */
private fun ApplicationRequest.toPlatform() =
  queryParameters["platform"]?.trim()?.takeIf { it.isNotEmpty() }
    ?: call.request.headers["X-Target-Platform"]
      ?.trim()
      ?.takeIf { it.isNotEmpty() }

/**
 * Checks that the targeted platform has a ready-made preset, so that a client typo
 * is reported instead of silently yielding an empty set of preferences.
 *
 * @return `true` if the client targets no platform or a known one.
 */
private fun ApplicationRequest.hasKnownPlatform() =
  toPlatform()?.lowercase()?.let { it in MediaPreferences.knownPlatforms } ?: true

/**
 * Combines the request's explicit stream-type and DRM preferences with the platform preset
 * from the `platform` query parameter (or `X-Target-Platform` header).
 *
 * Explicit preferences take precedence; the preset fills whichever list the client omitted.
 *
 * @return The effective [MediaPreferences] for this request.
 */
private fun ApplicationRequest.toMediaPreferences(): MediaPreferences {
  val platformPreferences = toPlatform()?.toPlatformPreferences() ?: MediaPreferences()

  return MediaPreferences(
    mimeTypePreferences =
      this.toMimeTypePreferences().takeIf { it.isNotEmpty() }
        ?: platformPreferences.mimeTypePreferences,
    drmPreferences = this.toDrmPreferences().takeIf { it.isNotEmpty() } ?: platformPreferences.drmPreferences,
  )
}
