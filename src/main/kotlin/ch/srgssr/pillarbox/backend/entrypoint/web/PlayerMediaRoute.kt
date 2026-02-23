package ch.srgssr.pillarbox.backend.entrypoint.web

import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.PlayerMediaResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toPlayerResponse
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.map

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
    val mediaFlow = mediaRepository.getAll(limit, offset)

    call.respond(mediaFlow.map { toResponse(it, call) })
  }

  get("/{id}") {
    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
    val media = mediaRepository.find(id) ?: return@get call.respond(HttpStatusCode.NotFound)
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
      // Supported Headers:
      // - X-Accept-Stream-Type: Preferred Source MIME type
      // - X-Accept-DRM: Preferred DRM key system
      toResponse = { media, call ->
        val preferredStream = call.request.headers["X-Accept-Stream-Type"]
        val preferredDRM = call.request.headers["X-Accept-DRM"]
        // TODO Add a dummy "platform" header that retrieves a fixed config.
        // TODO Add a "X-Accept-Security-Level" for DRM

        media.toPlayerResponse(
          mimeType = preferredStream,
          keySystem = preferredDRM,
        )
      },
    )
  }
}
