package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.auth.AuthenticatedUserPlugin
import ch.srgssr.pillarbox.backend.entrypoint.web.api.Navigation
import ch.srgssr.pillarbox.backend.persistence.folder.FolderPermissionRepository
import ch.srgssr.pillarbox.backend.persistence.folder.FolderRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import ch.srgssr.pillarbox.backend.persistence.team.TeamRepository
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

/**
 * Registers the web console routes.
 *
 * @param mediaRepository Repository used to read and persist media items.
 * @param folderRepository Repository used to read and persist folder structure.
 * @param folderPermissionRepository Repository used to read and manage folder grants.
 * @param userRepository Repository used to resolve and search users for grants.
 * @param teamRepository Repository used to resolve and search teams for grants.
 */
fun Route.console(
  mediaRepository: MediaRepository,
  folderRepository: FolderRepository,
  folderPermissionRepository: FolderPermissionRepository,
  userRepository: UserRepository,
  teamRepository: TeamRepository,
) {
  authenticate("pillarbox-session") {
    install(AuthenticatedUserPlugin)

    staticResources("/static", "static")

    route(Navigation.CONSOLE) {
      homePage(mediaRepository, folderRepository, folderPermissionRepository, userRepository, teamRepository)
      editorPage(mediaRepository, folderRepository)
      usersPage(userRepository)
      teamsPage(teamRepository)
    }
  }
}
