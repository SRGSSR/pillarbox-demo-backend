package ch.srgssr.pillarbox.backend.bootstrap

import ch.srgssr.pillarbox.backend.adapter.persistence.folder.FolderPermissionRepository
import ch.srgssr.pillarbox.backend.adapter.persistence.folder.FolderRepository
import ch.srgssr.pillarbox.backend.adapter.persistence.media.MediaRepository
import ch.srgssr.pillarbox.backend.adapter.persistence.session.SessionRepository
import ch.srgssr.pillarbox.backend.adapter.persistence.team.TeamRepository
import ch.srgssr.pillarbox.backend.adapter.persistence.user.UserRepository
import ch.srgssr.pillarbox.backend.domain.port.FolderCatalog
import ch.srgssr.pillarbox.backend.domain.port.FolderGrants
import ch.srgssr.pillarbox.backend.domain.port.MediaCatalog
import ch.srgssr.pillarbox.backend.domain.port.SessionCatalog
import ch.srgssr.pillarbox.backend.domain.port.TeamCatalog
import ch.srgssr.pillarbox.backend.domain.port.UserCatalog
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module providing the persistence layer. Each repository is additionally bound
 * to the domain port it implements, so routes and application services resolve the
 * interface while the Exposed implementation stays an adapter detail.
 *
 * @see MediaRepository
 * @see SessionRepository
 * @see UserRepository
 * @see FolderRepository
 * @see TeamRepository
 */
fun persistenceModule() =
  module {
    single { MediaRepository(get()) } bind MediaCatalog::class
    single { SessionRepository(get(), get()) } bind SessionCatalog::class
    single { UserRepository(get()) } bind UserCatalog::class
    single { FolderRepository(get()) } bind FolderCatalog::class
    single { FolderPermissionRepository(get()) } bind FolderGrants::class
    single { TeamRepository(get()) } bind TeamCatalog::class
  }
