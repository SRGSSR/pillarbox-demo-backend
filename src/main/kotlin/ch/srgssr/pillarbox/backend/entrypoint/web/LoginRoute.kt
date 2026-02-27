package ch.srgssr.pillarbox.backend.entrypoint.web

import ch.srgssr.pillarbox.backend.auth.SessionId
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Registers the OAuth2 login callback route.
 * This function handles the "exchange" phase of the OAuth2 flow. It:
 *
 * 1. Intercepts the authorization code from the identity provider.
 * 2. Retrieves the [OAuthAccessTokenResponse.OAuth2] principal.
 * 3. Maps the OAuth token to a new internal [Session].
 * 4. Persists the session and issues a [SessionId] cookie to the client.
 *
 * @param sessionRepository The persistence layer used to store the newly created user session.
 */
fun Route.login(sessionRepository: SessionRepository) {
  authenticate("pillarbox-oauth") {
    get(Navigation.LOGIN) { }
    get(Navigation.CALLBACK) {
      call.principal<OAuthAccessTokenResponse.OAuth2>()?.let { principal ->
        val now = Clock.System.now()
        val session =
          Session(
            sessionId = UUID.randomUUID().toString(),
            accessToken = principal.accessToken,
            expiresAt = principal.expiresIn.let { now + it.seconds },
          )

        sessionRepository.save(session.sessionId, session)
        call.sessions.set(SessionId(session.sessionId))
        call.respondRedirect(Navigation.CONSOLE)
      }
    }
  }
}
