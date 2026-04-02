package ch.srgssr.pillarbox.backend.entrypoint.web

import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.PlayerMediaResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toPlayerResponse
import ch.srgssr.pillarbox.backend.entrypoint.web.service.MediaSourceSelector
import ch.srgssr.pillarbox.backend.entrypoint.web.service.toDrmPreferences
import ch.srgssr.pillarbox.backend.io.parseHeaderList
import ch.srgssr.pillarbox.backend.io.parseParamList
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

/**
 * Generic helper to register player-facing media endpoints.
 *
 * @param Res The Response DTO type.
 * @param mediaRepository The repository to fetch media from.
 * @param toResponse Mapping function that transforms the domain [Media] into [Res],
 */
inline fun <reified Res : Any> Route.playerMediaEndpoints(
  mediaRepository: MediaRepository,
  crossinline toResponse: suspend (Media, ApplicationCall) -> Res,
) {
  get {
    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
    val mediaFlow = mediaRepository.getAll(limit, offset, filter = { MediaTable.deleted eq false })

    call.respond(mediaFlow.map { toResponse(it, call) })
  }

  get("/{id}") {
    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)

    val media =
      mediaRepository.findOne {
        (MediaTable.id eq id) and (MediaTable.deleted eq false)
      } ?: return@get call.respond(HttpStatusCode.NotFound)

    call.respond(toResponse(media, call))
  }
}

/**
 * Configures the versioned player media routes.
 *
 * @param mediaRepository The repository used to manage media entities.
 */
fun Route.playerMedia(mediaRepository: MediaRepository) {
  route("v1/player/media") {
    playerMediaEndpoints<PlayerMediaResponseV1>(
      mediaRepository = mediaRepository,
      // Supported query parameters (take precedence over headers):
      // - stream-type:     Preferred source MIME type (e.g. "application/dash+xml,application/x-mpegURL")
      // - drm:             Preferred DRM key system   (e.g. "com.widevine.alpha")
      //
      // Supported headers (fallback when query parameters are absent):
      // - X-Accept-Stream-Type:     Preferred source MIME type
      // - X-Accept-DRM:             Preferred DRM key system
      toResponse = { media, call ->
        val mimeTypes =
          call.request.queryParameters
            .parseParamList("stream-type")
            .ifEmpty { call.request.headers.parseHeaderList("X-Accept-Stream-Type") }
        val drmPreferences =
          call.request.queryParameters
            .parseParamList("drm")
            .ifEmpty { call.request.headers.parseHeaderList("X-Accept-DRM") }
            .toDrmPreferences()

        media.toPlayerResponse(
          MediaSourceSelector(mimeTypes, drmPreferences),
        )
      },
    )
  }
}
