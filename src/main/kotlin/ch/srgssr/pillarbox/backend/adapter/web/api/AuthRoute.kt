package ch.srgssr.pillarbox.backend.adapter.web.api

import ch.srgssr.pillarbox.backend.adapter.web.http.buildCallbackUrl
import ch.srgssr.pillarbox.backend.application.auth.OpenIDDiscovery
import ch.srgssr.pillarbox.backend.application.auth.UserManager
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.domain.model.SessionId
import ch.srgssr.pillarbox.backend.domain.port.SessionCatalog
import com.auth0.jwt.JWT
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Registers the authentication routes (login, OAuth callback, and logout).
 *
 * **Login / Callback** – Handles the OAuth2 "exchange" phase:
 *
 * 1. Intercepts the authorization code from the identity provider.
 * 2. Retrieves the [OAuthAccessTokenResponse.OAuth2] principal.
 * 3. Decodes the access token payload to fetch the user's profile
 *    and upsert the local user record, marking the login timestamp.
 * 4. Maps the OAuth token to a new internal [Session] linked to the local user.
 * 5. Persists the session and issues a [SessionId] cookie to the client.
 *
 * The cookie carries the raw session id; only its hash is persisted, so the cookie
 * credential cannot be recovered from the database.
 *
 * **Logout** – Clears the server-side session and cookie, then redirects to
 * the Keycloak end-session endpoint to terminate the SSO session.
 *
 * @param sessionCatalog The persistence port used to store and remove sessions.
 * @param userManager Used to upsert the local user record on login.
 * @param discovery The OIDC discovery metadata, used to resolve the end-session endpoint.
 */
fun Route.auth(
  sessionCatalog: SessionCatalog,
  userManager: UserManager,
  discovery: OpenIDDiscovery,
) {
  authenticate("pillarbox-oauth") {
    get(Navigation.LOGIN) { }
    get(Navigation.CALLBACK) {
      val sessionId = SessionId()
      val session =
        call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()?.let {
          val payload = JWT.decode(it.accessToken)
          userManager.upsert(payload)
          it.toSession(sessionId.value, payload.subject)
        } ?: return@get call.respondRedirect(Navigation.LOGIN)

      sessionCatalog.open(session)

      call.sessions.set(sessionId)
      call.respondRedirect(Navigation.CONSOLE)
    }
  }

  get(Navigation.LOGOUT) {
    val session =
      call.sessions.get<SessionId>()?.let {
        call.sessions.clear<SessionId>()
        sessionCatalog.find(it)
      } ?: return@get call.respondRedirect(Navigation.LOGIN)

    sessionCatalog.delete(session.sessionId)

    call.respondRedirect(
      discovery.endSessionEndpoint +
        "?post_logout_redirect_uri=${call.request.buildCallbackUrl(Navigation.LOGIN)}" +
        session.idToken?.let { "&id_token_hint=$it" }.orEmpty(),
    )
  }
}

private fun OAuthAccessTokenResponse.OAuth2.toSession(
  sessionId: String,
  subject: String,
) = Session(
  sessionId = sessionId,
  accessToken = accessToken,
  refreshToken = refreshToken,
  idToken = extraParameters["id_token"],
  expiresAt = expiresIn.let { Clock.System.now() + it.seconds },
  oidcSub = subject,
)
