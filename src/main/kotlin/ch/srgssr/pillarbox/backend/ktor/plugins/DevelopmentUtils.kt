package ch.srgssr.pillarbox.backend.ktor.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.application.log

/**
 * Optimizes server networking for the local development environment.
 * In development, hot-reloads cause the application context to restart frequently.
 * This function ensures that client connections are handled in a way that prevents
 * state conflicts between reloads.
 */
fun Application.configureDevelopmentDefaults() {
  if (developmentMode) {
    install(
      createApplicationPlugin("DevNetworkingPlugin") {
        onCall { call ->
          call.response.headers.append("Connection", "close")
        }
      },
    )
    log.info("Development networking optimized (Keep-Alive disabled)")
  }
}
