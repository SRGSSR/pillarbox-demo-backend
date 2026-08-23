package ch.srgssr.pillarbox.backend.bootstrap

import ch.srgssr.pillarbox.backend.application.authz.PermissionChecker
import org.koin.dsl.module

/**
 * Defines the Koin module for authorization.
 *
 * Provides the [PermissionChecker] used to evaluate folder and media write access for the
 * authenticated user.
 *
 * @return A Koin [Module] containing the authorization infrastructure definitions.
 */
fun authzModule() =
  module {
    single {
      PermissionChecker(
        folderGrants = get(),
        folderCatalog = get(),
        teamCatalog = get(),
      )
    }
  }
