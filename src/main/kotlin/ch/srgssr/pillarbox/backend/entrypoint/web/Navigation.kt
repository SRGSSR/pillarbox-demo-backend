package ch.srgssr.pillarbox.backend.entrypoint.web

/**
 * Centralized registry for web-based navigation paths.
 *
 * This object ensures consistency between route definitions and redirects
 * across the application, preventing hardcoded string errors in the
 * authentication and console layers.
 */
object Navigation {
  /**
   * The entry point for the user interface.
   */
  const val CONSOLE = "/console"

  /**
   * The challenge endpoint for the OAuth2 interactive login flow.
   * Navigating here triggers the redirect to the OIDC identity provider.
   */
  const val LOGIN = "/login"

  /**
   * The callback endpoint where the OIDC provider returns the authorization code.
   * This route is responsible for session creation and redirection to the [CONSOLE].
   */
  const val CALLBACK = "/callback"
}
