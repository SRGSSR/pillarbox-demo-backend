package ch.srgssr.pillarbox.backend.persistence

import ch.srgssr.pillarbox.backend.persistence.media.MediaRepository
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Koin module providing the persistence layer. This module is responsible for providing
 * instances of repositories throughout the Pillarbox backend.
 *
 * @see MediaRepository
 */
fun persistenceModule() =
  module {
    single { MediaRepository() }
  }
