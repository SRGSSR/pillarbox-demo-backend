package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.auth.AuthenticatedUserPlugin
import ch.srgssr.pillarbox.backend.entrypoint.web.api.Navigation
import ch.srgssr.pillarbox.backend.persistence.folder.FolderRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

/**
 * Registers the web console routes.
 *
 * @param mediaRepository Repository used to read and persist media items.
 * @param folderRepository Repository used to read and persist folder structure.
 */
fun Route.console(
  mediaRepository: MediaRepository,
  folderRepository: FolderRepository,
) {
  authenticate("pillarbox-session") {
    install(AuthenticatedUserPlugin)

    staticResources("/static", "static")

    route(Navigation.CONSOLE) {
      homePage(mediaRepository, folderRepository)
      editorPage(mediaRepository, folderRepository)
    }
  }
}
