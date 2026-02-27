package ch.srgssr.pillarbox.backend.persistence

import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import ch.srgssr.pillarbox.backend.persistence.media.MediaTable
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
import ch.srgssr.pillarbox.backend.persistence.session.SessionTable
import org.jetbrains.exposed.v1.core.Table
import org.koin.dsl.module

/**
 * Koin module providing the persistence layer. This module is responsible for providing
 * instances of repositories throughout the Pillarbox backend.
 *
 * @see MediaRepository
 */
fun persistenceModule() =
  module {
    single<List<Table>> {
      listOf(MediaTable, SessionTable)
    }
    single { MediaRepository(get()) }
    single { SessionRepository(get()) }
  }
