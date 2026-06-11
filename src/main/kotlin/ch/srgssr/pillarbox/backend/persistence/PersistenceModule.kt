package ch.srgssr.pillarbox.backend.persistence

import ch.srgssr.pillarbox.backend.persistence.folder.FolderMediaTable
import ch.srgssr.pillarbox.backend.persistence.folder.FolderRepository
import ch.srgssr.pillarbox.backend.persistence.folder.FolderTable
import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaTable
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
import ch.srgssr.pillarbox.backend.persistence.session.SessionTable
import ch.srgssr.pillarbox.backend.persistence.team.TeamMemberTable
import ch.srgssr.pillarbox.backend.persistence.team.TeamRepository
import ch.srgssr.pillarbox.backend.persistence.team.TeamTable
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import ch.srgssr.pillarbox.backend.persistence.user.UserTable
import org.jetbrains.exposed.v1.core.Table
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
    single<List<Table>> {
      listOf(MediaTable, SessionTable, UserTable, FolderTable, FolderMediaTable, TeamTable, TeamMemberTable)
    }
    single { MediaRepository(get()) }
    single { SessionRepository(get(), get()) }
    single { UserRepository(get()) }
    single { FolderRepository(get()) }
    single { TeamRepository(get()) }
  }
