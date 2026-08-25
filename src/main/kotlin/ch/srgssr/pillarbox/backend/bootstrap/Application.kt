package ch.srgssr.pillarbox.backend.bootstrap

import ch.srgssr.pillarbox.backend.adapter.web.api.auth
import ch.srgssr.pillarbox.backend.adapter.web.api.folder
import ch.srgssr.pillarbox.backend.adapter.web.api.media
import ch.srgssr.pillarbox.backend.adapter.web.api.playerMedia
import ch.srgssr.pillarbox.backend.adapter.web.api.team
import ch.srgssr.pillarbox.backend.adapter.web.api.user
import ch.srgssr.pillarbox.backend.adapter.web.console.console
import ch.srgssr.pillarbox.backend.adapter.web.http.PillarboxPebbleExtension
import ch.srgssr.pillarbox.backend.adapter.web.http.configureDevelopmentDefaults
import ch.srgssr.pillarbox.backend.adapter.web.http.configureOidc
import ch.srgssr.pillarbox.backend.adapter.web.http.installSession
import ch.srgssr.pillarbox.backend.adapter.web.http.toDisplayConfig
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.netty.EngineMain
import io.ktor.server.pebble.Pebble
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.routing.routing
import io.pebbletemplates.pebble.loader.ClasspathLoader
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
  if (environment.config.enableProxyHeaders()) {
    install(XForwardedHeaders)
  }

  install(Compression) {
    gzip()
  }

  val displayConfig = environment.config.toDisplayConfig()

  install(Pebble) {
    loader(
      ClasspathLoader().apply {
        prefix = "templates"
      },
    )
    extension(PillarboxPebbleExtension(displayConfig.timeZone))
  }

  install(Koin) {
    slf4jLogger()
    modules(
      module { single { environment.config } },
      databaseModule(),
      persistenceModule(),
      jsonModule(),
      httpClientModule(),
      integrationLayerModule(),
      authenticationModule(),
      authzModule(),
    )
  }

  install(Authentication) {
    configureOidc(
      authConfig = this@module.get(),
      httpClient = this@module.get(),
      sessionManager = this@module.get(),
      userCatalog = this@module.get(),
      policy = this@module.get(),
    )
  }

  installSession(get())
  install(ContentNegotiation) { json(this@module.get()) }

  configureDevelopmentDefaults()

  // Setup HTTP Routing
  routing {
    auth(get(), get(), get())
    media(get(), get())
    folder(get(), get(), get(), get(), get())
    user(get(), get())
    team(get(), get())
    console(get(), get(), get(), get(), get(), get())
    playerMedia(get(), get())
  }

  monitor.subscribe(ApplicationStopped) {
    stopKoin()
  }
}

/**
 * Checks the application configuration to determine if proxy header support is enabled.
 *
 * This looks for the `ktor.deployment.enable_forwarded_headers` property.
 *
 * @return `true` if the property is explicitly set to "true", `false` otherwise
 * (including if the property is missing).
 */
fun ApplicationConfig.enableProxyHeaders(): Boolean =
  propertyOrNull("ktor.deployment.enable_forwarded_headers")
    ?.getString()
    ?.toBoolean() ?: false
