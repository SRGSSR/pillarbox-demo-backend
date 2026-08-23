package ch.srgssr.pillarbox.backend.adapter.web.console

import ch.srgssr.pillarbox.backend.adapter.web.api.Navigation
import ch.srgssr.pillarbox.backend.adapter.web.http.AuthenticatedUserPlugin
import ch.srgssr.pillarbox.backend.application.media.ImportMediaFromUrn
import ch.srgssr.pillarbox.backend.domain.port.FolderCatalog
import ch.srgssr.pillarbox.backend.domain.port.FolderGrants
import ch.srgssr.pillarbox.backend.domain.port.MediaCatalog
import ch.srgssr.pillarbox.backend.domain.port.TeamCatalog
import ch.srgssr.pillarbox.backend.domain.port.UserCatalog
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

/**
 * Registers the web console routes.
 *
 * @param mediaCatalog Repository used to read and persist media items.
 * @param folderCatalog Repository used to read and persist folder structure.
 * @param folderGrants Repository used to read and manage folder grants.
 * @param userCatalog Repository used to resolve and search users for grants.
 * @param teamCatalog Repository used to resolve and search teams for grants.
 * @param importMediaFromUrn Use case importing media metadata by URN.
 */
fun Route.console(
  mediaCatalog: MediaCatalog,
  folderCatalog: FolderCatalog,
  folderGrants: FolderGrants,
  userCatalog: UserCatalog,
  teamCatalog: TeamCatalog,
  importMediaFromUrn: ImportMediaFromUrn,
) {
  authenticate("pillarbox-session") {
    install(AuthenticatedUserPlugin)

    staticResources("/static", "static")

    route(Navigation.CONSOLE) {
      homePage(mediaCatalog, folderCatalog, folderGrants, userCatalog, teamCatalog)
      editorPage(mediaCatalog, folderCatalog, importMediaFromUrn)
      usersPage(userCatalog)
      teamsPage(teamCatalog, userCatalog)
    }
  }
}
