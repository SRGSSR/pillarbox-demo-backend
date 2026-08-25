package ch.srgssr.pillarbox.backend.adapter.web.api

import ch.srgssr.pillarbox.backend.adapter.web.api.dto.ImportMediaRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.MediaRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.TagBatchUpdateRequestV1
import ch.srgssr.pillarbox.backend.adapter.web.api.dto.toMediaResponseV1
import ch.srgssr.pillarbox.backend.adapter.web.http.AuthenticatedUserPlugin
import ch.srgssr.pillarbox.backend.adapter.web.http.toFolderScope
import ch.srgssr.pillarbox.backend.adapter.web.http.toMediaVisibility
import ch.srgssr.pillarbox.backend.adapter.web.http.toQuerySlice
import ch.srgssr.pillarbox.backend.adapter.web.http.withMediaWrite
import ch.srgssr.pillarbox.backend.adapter.web.http.withRole
import ch.srgssr.pillarbox.backend.application.media.ImportMediaFromUrn
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
 * @param importMediaFromUrn The use case that fetches a media from the Integration Layer by URN.
 */
@SuppressWarnings("LongMethod")
fun Route.media(
  mediaCatalog: MediaCatalog,
  importMediaFromUrn: ImportMediaFromUrn,
) {
  authenticate("pillarbox-jwt", "pillarbox-session") {
    route("v1/media") {
      install(AuthenticatedUserPlugin)

      get {
        val slice = call.request.queryParameters.toQuerySlice()
        val query = call.request.queryParameters["q"]
        val visibility =
          call.request.queryParameters.toMediaVisibility()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Unknown visibility; use 'active' or 'deleted'")
        val scope =
          call.request.queryParameters.toFolderScope()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Unknown scope; use 'all' or 'unassigned'")
        call.respond(
          mediaCatalog
            .page(MediaCriteria(visibility = visibility, scope = scope, text = query), slice)
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

    route("v1/media-import") {
      install(AuthenticatedUserPlugin)

      withRole(Role.WRITE) {
        post {
          val request = call.receive<ImportMediaRequestV1>()
          if (request.urn.isBlank()) return@post call.respond(HttpStatusCode.BadRequest)

          withMediaWrite(request.urn.takeIf { mediaCatalog.exists(it) }) {
            val import =
              importMediaFromUrn(request.urn)
                ?: return@withMediaWrite call.respond(HttpStatusCode.BadGateway)

            val media = mediaCatalog.save(import.media)
            call.respond(HttpStatusCode.Created, media.toMediaResponseV1())
          }
        }
      }
    }
  }
}
