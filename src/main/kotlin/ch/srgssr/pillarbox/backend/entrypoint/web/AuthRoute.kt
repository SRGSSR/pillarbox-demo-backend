package ch.srgssr.pillarbox.backend.entrypoint.web

import ch.srgssr.pillarbox.backend.auth.OpenIDDiscovery
import ch.srgssr.pillarbox.backend.auth.SessionId
import ch.srgssr.pillarbox.backend.auth.UserInfoProvider
import ch.srgssr.pillarbox.backend.auth.buildCallbackUrl
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
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
 * **Logout** – Clears the server-side session and cookie, then redirects to
 * the Keycloak end-session endpoint to terminate the SSO session.
 *
 * @param sessionRepository The persistence layer used to store and remove sessions.
 * @param userInfoProvider Fetches the user's OIDC profile and synchronises the local user record.
 * @param discovery The OIDC discovery metadata, used to resolve the end-session endpoint.
 */
fun Route.auth(
  sessionRepository: SessionRepository,
  userInfoProvider: UserInfoProvider,
  discovery: OpenIDDiscovery,
) {
  authenticate("pillarbox-oauth") {
    get(Navigation.LOGIN) { }
    get(Navigation.CALLBACK) {
      val session =
        call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()?.let {
          val user = userInfoProvider.fetchAndSync(it.accessToken, updateLastLogin = true)
          it.toSession(user?.id)
        } ?: return@get call.respondRedirect(Navigation.LOGIN)

      sessionRepository.save(session)

      call.sessions.set(SessionId(session.sessionId))
      call.respondRedirect(Navigation.CONSOLE)
    }
  }

  get(Navigation.LOGOUT) {
    val session =
      call.sessions.get<SessionId>()?.let {
        call.sessions.clear<SessionId>()
        sessionRepository.find(it.value)
      } ?: return@get call.respondRedirect(Navigation.LOGIN)

    sessionRepository.delete(session.sessionId)

    call.respondRedirect(
      discovery.endSessionEndpoint +
        "?post_logout_redirect_uri=${call.request.buildCallbackUrl(Navigation.LOGIN)}" +
        session.idToken?.let { "&id_token_hint=$it" }.orEmpty(),
    )
  }
}

private fun OAuthAccessTokenResponse.OAuth2.toSession(userId: String?) =
  Session(
    accessToken = accessToken,
    refreshToken = refreshToken,
    idToken = extraParameters["id_token"],
    expiresAt = expiresIn.let { Clock.System.now() + it.seconds },
    userId = userId,
  )
