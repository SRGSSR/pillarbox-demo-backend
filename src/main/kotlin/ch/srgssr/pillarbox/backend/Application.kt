package ch.srgssr.pillarbox.backend

import ch.srgssr.pillarbox.backend.auth.authenticationModule
import ch.srgssr.pillarbox.backend.auth.configureOidc
import ch.srgssr.pillarbox.backend.auth.installSession
import ch.srgssr.pillarbox.backend.db.databaseModule
import ch.srgssr.pillarbox.backend.entrypoint.web.dashboard
import ch.srgssr.pillarbox.backend.entrypoint.web.login
import ch.srgssr.pillarbox.backend.entrypoint.web.media
import ch.srgssr.pillarbox.backend.entrypoint.web.playerMedia
import ch.srgssr.pillarbox.backend.io.httpClientModule
import ch.srgssr.pillarbox.backend.io.jsonModule
import ch.srgssr.pillarbox.backend.ktor.plugins.configureDevelopmentDefaults
import ch.srgssr.pillarbox.backend.persistence.persistenceModule
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import org.koin.core.context.stopKoin
import org.koin.dsl.module
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
  install(Koin) {
    slf4jLogger()
    modules(
      module { single { environment.config } },
      databaseModule(),
      persistenceModule(),
      jsonModule(),
      httpClientModule(),
      authenticationModule(),
    )
  }

  install(Authentication) {
    configureOidc(
      authConfig = this@module.get(),
      httpClient = this@module.get(),
      sessionManager = this@module.get(),
      policy = this@module.get(),
    )
  }

  installSession(get())

  install(ContentNegotiation) { json(this@module.get()) }

  configureDevelopmentDefaults()

  // Setup HTTP Routing
  routing {
    login(get())
    media(get())
    playerMedia(get())
    dashboard()
  }

  monitor.subscribe(ApplicationStopped) {
    stopKoin()
  }
}
