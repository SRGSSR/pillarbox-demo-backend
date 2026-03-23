package ch.srgssr.pillarbox.backend.entrypoint.web

import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.log.debug
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.log.trace
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.htmx.hx
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

private object ConsoleRoute

private val logger = ConsoleRoute.logger()

/**
 * Basic dashboard entry point protected by SSO session.
 */
@OptIn(ExperimentalKtorApi::class)
@SuppressWarnings("LongMethod")
fun Route.console(mediaRepository: MediaRepository) {
  authenticate("pillarbox-session") {
    staticResources("/static", "static")

    route(Navigation.CONSOLE) {
      get {
        call.respond(
          PebbleContent("modules/home/home.page.peb", emptyMap()),
        )
      }

      hx.get("media") {
        val page = call.parameters["page"]?.toIntOrNull() ?: 0
        val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 15
        val offset = (page * pageSize).toLong()

        logger.debug { "Fetching media grid: page=$page, pageSize=$pageSize, offset=$offset" }

        val result = mediaRepository.getAllPaginated(limit = pageSize, offset = offset)

        call.respond(
          PebbleContent(
            "shared/fragments/media-grid.fragment.peb",
            mapOf(
              "result" to result,
              "nextPage" to page + 1,
            ),
          ),
        )
      }

      hx.post("media") {
        val media = call.receive<Media>()

        mediaRepository.save(media.id, media)

        call.response.headers.append("HX-Redirect", "/console")
        call.respond(HttpStatusCode.OK)
      }

      get("media/editor/{id?}") {
        val media =
          call.parameters["id"]
            ?.let { mediaRepository.find(it) }
            ?.also { logger.debug { "Opening editor for media: $it" } }

        call.respond(
          PebbleContent(
            "modules/media/editor.page.peb",
            media?.let { mapOf("item" to media) } ?: emptyMap(),
          ),
        )
      }

      get("media/editor/{id}/duplicate") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.NotFound)

        val media =
          mediaRepository
            .find(id)
            ?.also { logger.debug { "Opening editor with duplicate data from original ID: $id" } }
            ?: return@get call.respond(HttpStatusCode.NotFound)

        call.respond(
          PebbleContent(
            "modules/media/editor.page.peb",
            mapOf("item" to media.copy(id = "")),
          ),
        )
      }

      hx.get("/media/editor/fragments/{fragment}") {
        val fragment =
          EditorFragment.find(call.parameters["fragment"])
            ?: return@get call.respond(HttpStatusCode.NotFound)
        val index = call.request.queryParameters["index"]?.toInt() ?: 0

        logger.trace { "Rendering fragment: ${fragment.name} at index $index" }

        call.respondTemplate(
          fragment.template,
          mapOf("index" to index),
        )
      }

      hx.delete("media/{id}") {
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.NotFound)

        logger.info { "Attempting to delete media with ID: $id" }

        val deleted = mediaRepository.delete(id)

        if (deleted) {
          call.respond(HttpStatusCode.OK)
        } else {
          call.respond(HttpStatusCode.NotFound)
        }
      }
    }
  }
}

/**
 * Represents the various dynamic UI components (fragments) that can be
 * injected into the Media Editor via HTMX.
 *
 * @property id The string identifier used in the URL path.
 */
private enum class EditorFragment(
  val id: String,
) {
  CHAPTER("chapter"),
  TIME_RANGE("time-range"),
  SOURCE("source"),
  SUBTITLE("subtitle"),
  DRM("drm"),
  ;

  /**
   * The resolved classpath to the Pebble template for this fragment.
   */
  val template: String
    get() = "modules/media/fragments/$id-row.fragment.peb"

  companion object {
    /**
     * Matches a URL path parameter to an [EditorFragment].
     *
     * @param value The raw string from the {fragment} path parameter.
     *
     * @return The matching fragment, or null if the ID is not recognized.
     */
    fun find(value: String?): EditorFragment? = entries.find { it.id == value }
  }
}
