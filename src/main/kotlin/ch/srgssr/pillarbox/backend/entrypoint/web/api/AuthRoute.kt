package ch.srgssr.pillarbox.backend.entrypoint.web.api

import ch.srgssr.pillarbox.backend.auth.OpenIDDiscovery
import ch.srgssr.pillarbox.backend.auth.SessionId
import ch.srgssr.pillarbox.backend.auth.UserManager
import ch.srgssr.pillarbox.backend.auth.buildCallbackUrl
import ch.srgssr.pillarbox.backend.db.EncryptionService
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
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
 * 3. Calls the OIDC UserInfo endpoint via [userInfoProvider] to fetch the user's profile
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
 * @param sessionRepository The persistence layer used to store and remove sessions.
 * @param encryptionService Used to hash the cookie session id into its persisted form.
 * @param discovery The OIDC discovery metadata, used to resolve the end-session endpoint.
 */
fun Route.auth(
  sessionRepository: SessionRepository,
  encryptionService: EncryptionService,
  userManager: UserManager,
  discovery: OpenIDDiscovery,
) {
  /**
   * The hashed value of the session id that is searchable in the database.
   */
  fun SessionId.hashedValue() = encryptionService.hash(value)

  authenticate("pillarbox-oauth") {
    get(Navigation.LOGIN) { }
    get(Navigation.CALLBACK) {
      val sessionId = SessionId()
      val session =
        call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()?.let {
          val payload = JWT.decode(it.accessToken)
          userManager.upsert(payload)
          it.toSession(sessionId.hashedValue(), payload.subject)
        } ?: return@get call.respondRedirect(Navigation.LOGIN)

      sessionRepository.save(session)

      call.sessions.set(sessionId)
      call.respondRedirect(Navigation.CONSOLE)
    }
  }

  get(Navigation.LOGOUT) {
    val session =
      call.sessions.get<SessionId>()?.let {
        call.sessions.clear<SessionId>()
        sessionRepository.find(it.hashedValue())
      } ?: return@get call.respondRedirect(Navigation.LOGIN)

    sessionRepository.delete(session.sessionId)

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
