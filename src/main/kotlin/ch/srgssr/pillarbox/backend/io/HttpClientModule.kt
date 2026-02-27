package ch.srgssr.pillarbox.backend.io

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * A Koin module that provides a singleton instance of [HttpClient].
 *
 * @return A Koin [Module] containing the database infrastructure definitions.
 */
fun httpClientModule() =
  module {
    single {
      HttpClient(CIO) { install(ContentNegotiation) { json(get()) } }
    } onClose { it?.close() } // Always close the client!
  }
