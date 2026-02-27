package ch.srgssr.pillarbox.backend.auth

import ch.srgssr.pillarbox.backend.entrypoint.web.Navigation
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.oauth
import io.ktor.server.auth.session
import io.ktor.server.plugins.origin
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie

/**
 * Configures OIDC-related authentication providers for the application.
 *
 * This setup includes three distinct providers under a shared naming convention:
 * 1. **JWT**: For stateless API authentication using bearer tokens.
 * 2. **OAuth**: For the interactive authorization code flow with the identity provider.
 * 3. **Session**: For stateful authentication using a [SessionId] cookie.
 *
 * @param name The prefix used to register the authentication providers (default is "pillarbox").
 * @param authConfig The [AuthConfig] containing issuer and realm details.
 * @param httpClient The [HttpClient] used for back-channel OAuth token exchanges.
 * @param sessionManager The [SessionManager] responsible for validating existing sessions.
 * @param policy The [AuthenticationPolicy] used for JWT verification and OAuth settings lookup.
 */
fun AuthenticationConfig.configureOidc(
  name: String = "pillarbox",
  authConfig: AuthConfig,
  httpClient: HttpClient,
  sessionManager: SessionManager,
  policy: AuthenticationPolicy,
) {
  jwt("$name-jwt") {
    realm = authConfig.realm
    verifier(policy.jwkProvider, authConfig.issuer)
    validate { policy.verifyJwt(it) }
  }

  oauth("$name-oauth") {
    urlProvider = { request.buildCallbackUrl() }
    providerLookup = { policy.getOAuthSettings(name) }
    client = httpClient
  }

  session<SessionId>("$name-session") {
    validate { sessionManager.validate(it) }
    challenge { call.respondRedirect(Navigation.LOGIN) }
  }
}

/**
 * Builds a dynamic callback URL for OAuth redirection based on the incoming request's origin.
 *
 * This ensures the redirect URI matches the protocol, host, and port used by the client,
 * which is critical for environments behind proxies or load balancers.
 *
 * @param path The relative path for the OAuth callback (default is "/callback").
 *
 * @return A fully qualified URL string.
 */
fun ApplicationRequest.buildCallbackUrl(path: String = "/callback"): String {
  val o = origin
  return "${o.scheme}://${o.serverHost}:${o.serverPort}$path"
}

/**
 * Installs and configures the Ktor [Sessions] plugin with a signed cookie.
 *
 * The session cookie is configured with:
 * - **Security**: Signed using [SessionTransportTransformerMessageAuthentication] to prevent tampering.
 * - **HttpOnly**: Shielded from client-side JavaScript access to mitigate XSS risks.
 * - **SameSite**: Set to "Lax" to balance CSRF protection with usability.
 *
 * @param sessionConfig The [SessionConfig] defining timeouts, security flags, and the signing secret.
 */
fun Application.installSession(sessionConfig: SessionConfig) {
  install(Sessions) {
    cookie<SessionId>("PILLARBOX_SESSION_ID") {
      cookie.path = "/"
      cookie.maxAgeInSeconds = sessionConfig.timeoutSeconds
      cookie.secure = sessionConfig.isSecure
      cookie.httpOnly = true
      cookie.extensions["SameSite"] = "Lax"
      transform(SessionTransportTransformerMessageAuthentication(sessionConfig.cookieSecret.toByteArray()))
    }
  }
}
