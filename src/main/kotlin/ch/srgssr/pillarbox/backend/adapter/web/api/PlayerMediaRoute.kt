package ch.srgssr.pillarbox.backend.adapter.web.api

import ch.srgssr.pillarbox.backend.adapter.web.api.dto.toPlayerResponse
import ch.srgssr.pillarbox.backend.adapter.web.http.parseHeaderList
import ch.srgssr.pillarbox.backend.adapter.web.http.parseParamList
import ch.srgssr.pillarbox.backend.adapter.web.http.toQuerySlice
import ch.srgssr.pillarbox.backend.domain.catalog.FolderScope
import ch.srgssr.pillarbox.backend.domain.catalog.MediaCriteria
import ch.srgssr.pillarbox.backend.domain.catalog.MediaVisibility
import ch.srgssr.pillarbox.backend.domain.playback.MediaPreferences
import ch.srgssr.pillarbox.backend.domain.playback.MediaSourceSelector
import ch.srgssr.pillarbox.backend.domain.playback.toDrmPreferences
import ch.srgssr.pillarbox.backend.domain.playback.toPlatformPreferences
import ch.srgssr.pillarbox.backend.domain.port.FolderCatalog
import ch.srgssr.pillarbox.backend.domain.port.MediaCatalog
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.getOrFail

/**
 * Configures the versioned public player media routes under `v1/player/`.
 *
 * These endpoints return player-optimized responses with media sources selected
 * according to the client's stream-type and DRM preferences.
 *
 * @param mediaCatalog The repository used to manage media entities.
 * @param folderCatalog The repository used to verify folder existence.
 */
fun Route.playerMedia(
  mediaCatalog: MediaCatalog,
  folderCatalog: FolderCatalog,
) {
  route("v1/player/") {
    route("media") {
      get {
        if (!call.request.hasKnownPlatform()) return@get call.respond(HttpStatusCode.BadRequest, "Unknown platform")

        val slice = call.request.queryParameters.toQuerySlice()
        val mediaSourceSelector = call.request.toMediaSourceSelector()
        val query = call.request.queryParameters["q"]
        call.respond(
          mediaCatalog
            .page(MediaCriteria(visibility = MediaVisibility.PLAYABLE, text = query), slice)
            .items
            .map { it.toPlayerResponse(mediaSourceSelector) },
        )
      }

      get("/{id}") {
        if (!call.request.hasKnownPlatform()) return@get call.respond(HttpStatusCode.BadRequest, "Unknown platform")

        val id = call.parameters.getOrFail("id")

        val media =
          mediaCatalog.find(id, MediaVisibility.PLAYABLE)
            ?: return@get call.respond(HttpStatusCode.NotFound)

        call.respond(media.toPlayerResponse(call.request.toMediaSourceSelector()))
      }
    }

    route("folder") {
      get("/{id}/media") {
        if (!call.request.hasKnownPlatform()) return@get call.respond(HttpStatusCode.BadRequest, "Unknown platform")

        val id = call.parameters.getOrFail("id")
        if (!folderCatalog.exists(id)) return@get call.respond(HttpStatusCode.NotFound)

        val slice = call.request.queryParameters.toQuerySlice()
        val mediaSourceSelector = call.request.toMediaSourceSelector()
        call.respond(
          mediaCatalog
            .page(MediaCriteria(visibility = MediaVisibility.PLAYABLE, scope = FolderScope.In(id)), slice)
            .items
            .map { it.toPlayerResponse(mediaSourceSelector) },
        )
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
 * @return list of parsed [DrmPreference][ch.srgssr.pillarbox.backend.domain.playback.DrmPreference]s,
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
