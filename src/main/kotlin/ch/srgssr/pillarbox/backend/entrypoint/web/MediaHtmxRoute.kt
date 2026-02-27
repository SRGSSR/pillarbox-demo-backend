package ch.srgssr.pillarbox.backend.entrypoint.web

import io.ktor.server.auth.authenticate
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.p

/**
 * Basic dashboard entry point protected by SSO session.
 */
fun Route.dashboard() {
  authenticate("pillarbox-session") {
    route(Navigation.CONSOLE) {
      get {
        call.respondHtml {
          body {
            h1 { +"Pillarbox Console" }
            p { +"Logged in as an authorized user." }
          }
        }
      }
    }
  }
}
