package ch.srgssr.pillarbox.backend.persistence

import ch.srgssr.pillarbox.backend.persistence.folder.FolderPermissionRepository
import ch.srgssr.pillarbox.backend.persistence.folder.FolderRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
import ch.srgssr.pillarbox.backend.persistence.team.TeamRepository
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import org.koin.dsl.module

/**
 * Koin module providing the persistence layer. This module is responsible for providing
 * instances of repositories throughout the Pillarbox backend.
 *
 * @see MediaRepository
 * @see SessionRepository
 * @see UserRepository
 * @see FolderRepository
 * @see TeamRepository
 */
fun persistenceModule() =
  module {
    single { MediaRepository(get()) }
    single { SessionRepository(get(), get()) }
    single { UserRepository(get()) }
    single { FolderRepository(get()) }
    single { FolderPermissionRepository(get()) }
    single { TeamRepository(get()) }
  }
