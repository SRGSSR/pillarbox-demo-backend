package ch.srgssr.pillarbox.backend.io

import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Koin module providing a configured JSON serializer.
 *
 * The Serializer is configured to:
 * - Ignore unknown properties during deserialization.
 * - Remove explicit nulls.
 *
 * @see Json
 */
fun jsonModule() =
  module {
    single {
      Json {
        ignoreUnknownKeys = true
        explicitNulls = false
      }
    }
  }
