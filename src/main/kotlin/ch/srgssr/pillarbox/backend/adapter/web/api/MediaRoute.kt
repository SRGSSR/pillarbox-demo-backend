package ch.srgssr.pillarbox.backend.adapter.web.api

import ch.srgssr.pillarbox.backend.adapter.web.api.dto.MediaRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.TagBatchUpdateRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.toMediaResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.http.AuthenticatedUserPlugin
import ch.srgssr.pillarbox.backend.adapter.web.http.toQuerySlice
import ch.srgssr.pillarbox.backend.adapter.web.http.withMediaWrite
import ch.srgssr.pillarbox.backend.adapter.web.http.withRole
import ch.srgssr.pillarbox.backend.domain.catalog.MediaCriteria
import ch.srgssr.pillarbox.backend.domain.catalog.MediaVisibility
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.port.MediaCatalog
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
import io.ktor.server.util.getOrFail

/**
 * Configures the versioned media management routes.
 *
 * Mutations are guarded by `withMediaWrite`: media in restricted folders only accepts changes
 * from granted editors and administrators.
 *
 * @param mediaCatalog The repository used to manage media entities.
 */
@SuppressWarnings("LongMethod")
fun Route.media(mediaCatalog: MediaCatalog) {
  authenticate("pillarbox-jwt", "pillarbox-session") {
    route("v1/media") {
      install(AuthenticatedUserPlugin)

      get {
        val slice = call.request.queryParameters.toQuerySlice()
        val query = call.request.queryParameters["q"]
        call.respond(
          mediaCatalog
            .page(MediaCriteria(text = query), slice)
            .items
            .map { it.toMediaResponseV1() },
        )
      }

      get("/{id}") {
        val id = call.parameters.getOrFail("id")

        val media =
          mediaCatalog.find(id, MediaVisibility.ACTIVE)
            ?: return@get call.respond(HttpStatusCode.NotFound)

        call.respond(media.toMediaResponseV1())
      }

      withRole(Role.WRITE) {
        post {
          val media = call.receive<MediaRequestV1>().toMedia()

          // Saving is an upsert: overwriting an existing media is governed by its folder,
          // while a brand-new id has nothing to protect yet.
          withMediaWrite(media.id.takeIf { mediaCatalog.exists(it) }) {
            mediaCatalog.save(media)
            call.respond(HttpStatusCode.Created, media.toMediaResponseV1())
          }
        }

        patch("/{id}/tags") {
          val id = call.parameters.getOrFail("id")
          val request = call.receive<TagBatchUpdateRequestV1>()
          withMediaWrite(id) {
            mediaCatalog
              .updateTags(id) { request.apply(it) }
              ?.let { call.respond(HttpStatusCode.OK, it) }
              ?: call.respond(HttpStatusCode.NotFound)
          }
        }

        delete("/{id}") {
          val id = call.parameters.getOrFail("id")
          withMediaWrite(id) {
            mediaCatalog
              .softDelete(id)
              .takeIf { it }
              ?.let { call.respond(HttpStatusCode.NoContent) }
              ?: call.respond(HttpStatusCode.NotFound)
          }
        }
      }

      withRole(Role.ADMIN) {
        post("/{id}/restore") {
          val id = call.parameters.getOrFail("id")

          mediaCatalog
            .restore(id)
            .takeIf { it }
            ?.let { call.respond(HttpStatusCode.Created) }
            ?: call.respond(HttpStatusCode.NotFound)
        }
      }
    }
  }
}
