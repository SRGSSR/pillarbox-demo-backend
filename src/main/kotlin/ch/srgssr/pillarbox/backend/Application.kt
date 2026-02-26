package ch.srgssr.pillarbox.backend

import ch.srgssr.pillarbox.backend.db.databaseModule
import ch.srgssr.pillarbox.backend.db.toDatabaseConfig
import ch.srgssr.pillarbox.backend.entrypoint.web.media
import ch.srgssr.pillarbox.backend.entrypoint.web.playerMedia
import ch.srgssr.pillarbox.backend.io.jsonModule
import ch.srgssr.pillarbox.backend.ktor.plugins.configureDevelopmentDefaults
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.persistence.persistenceModule
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import org.koin.core.context.stopKoin
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/**
 * The main entry point for the Pillarbox Backend application.
 *
 * This uses the Netty engine as defined in the [EngineMain] configuration.
 * It initializes the server and starts listening for incoming requests.
 */
fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
  // Initialize Dependency Injection
  val config = environment.config

  install(Koin) {
    slf4jLogger()
    modules(
      databaseModule(config.toDatabaseConfig()),
      persistenceModule(),
      jsonModule(),
    )
  }

  configureDevelopmentDefaults()

  // Configure JSON serialization/deserialization for HTTP calls
  install(ContentNegotiation) {
    json(this@module.get())
  }

  // Setup HTTP Routing
  routing {
    media(get())
    playerMedia(get())
  }

  monitor.subscribe(ApplicationStopped) {
    stopKoin()
  }
}
