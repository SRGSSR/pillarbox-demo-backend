package ch.srgssr.pillarbox.backend.entrypoint.web

import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.MediaRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.MediaResponseV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.TagBatchUpdateRequestV1
import ch.srgssr.pillarbox.backend.entrypoint.web.dto.toMediaResponseV1
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

/**
 * Generic helper to register standard Media CRUD endpoints.
 *
 * This function abstracts the web layer logic (Ktor parameters, status codes, logging)
 * from the specific DTO types used. It allows the same endpoint logic to be reused
 * for different API versions or entry points.
 *
 * @param Req The Request DTO type.
 * @param Res The Response DTO type.
 * @param TagReq The DTO type used for tag update operations.
 * @param mediaRepository The repository for persistence operations.
 * @param toDomain Function to map the Request DTO to the [Media] domain model.
 * @param toResponse Function to map the [Media] domain model to the Response DTO.
 * @param applyTags Logic to apply [TagReq] to an existing list of tags.
 */
inline fun <reified Req : Any, reified Res : Any, reified TagReq : Any> Route.mediaEndpoints(
  mediaRepository: MediaRepository,
  crossinline toDomain: (Req) -> Media,
  crossinline toResponse: (Media) -> Res,
  crossinline applyTags: (TagReq, List<String>) -> List<String>,
) {
  get {
    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
    val mediaFlow = mediaRepository.getAll(limit, offset)

    call.respond(mediaFlow.map { toResponse(it) }.toList())
  }

  get("/{id}") {
    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
    val media = mediaRepository.find(id) ?: return@get call.respond(HttpStatusCode.NotFound)

    call.respond(toResponse(media))
  }

  post {
    val dto = call.receive<Req>()
    val media = toDomain(dto)

    mediaRepository.save(media.id, media)
    call.respond(HttpStatusCode.Created, toResponse(media))
  }

  patch("/{id}/tags") {
    val id = call.parameters["id"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
    val request = call.receive<TagReq>()

    mediaRepository
      .updateTags(id) { applyTags(request, it) }
      ?.let { call.respond(HttpStatusCode.OK, it) }
      ?: call.respond(HttpStatusCode.NotFound)
  }

  delete("/{id}") {
    val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)

    mediaRepository
      .delete(id)
      .takeIf { it }
      ?.let { call.respond(HttpStatusCode.NoContent) }
      ?: call.respond(HttpStatusCode.NotFound)
  }
}

/**
 * Configures the versioned media management routes.
 *
 * @param mediaRepository The repository used to manage media entities.
 */
fun Route.media(mediaRepository: MediaRepository) {
  // Entry point for the V1 media API.
  authenticate("pillarbox-jwt") {
    route("v1/media") {
      mediaEndpoints<MediaRequestV1, MediaResponseV1, TagBatchUpdateRequestV1>(
        mediaRepository = mediaRepository,
        toDomain = { it.toMedia() },
        toResponse = { it.toMediaResponseV1() },
        applyTags = { dto, tags -> dto.apply(tags) },
      )
    }
  }
}
