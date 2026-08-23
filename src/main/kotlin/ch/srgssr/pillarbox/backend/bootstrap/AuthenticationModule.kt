package ch.srgssr.pillarbox.backend.bootstrap

import ch.srgssr.pillarbox.backend.adapter.oidc.TokenProvider
import ch.srgssr.pillarbox.backend.adapter.web.http.AuthenticationPolicy
import ch.srgssr.pillarbox.backend.adapter.web.http.toSessionConfig
import ch.srgssr.pillarbox.backend.application.auth.AuthConfig
import ch.srgssr.pillarbox.backend.application.auth.OpenIDDiscovery
import ch.srgssr.pillarbox.backend.application.auth.SessionManager
import ch.srgssr.pillarbox.backend.application.auth.UserManager
import ch.srgssr.pillarbox.backend.application.auth.toAuthConfig
import ch.srgssr.pillarbox.backend.domain.port.IdentityProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.runBlocking
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Defines the Koin module for authentication and session management.
 *
 * This module provides:
 * 1. The [AuthConfig] and [SessionConfig] extracted from the application configuration.
 * 2. The [OpenIDDiscovery] document, fetched synchronously from the identity provider's
 * discovery URL. Note: This involves a [runBlocking] network call upon first resolution.
 * 3. A [TokenProvider] bound as the [IdentityProvider] port.
 * 4. A [UserManager] recording authenticated users.
 * 5. A [SessionManager] to handle user session lifecycles and token validation.
 * 6. An [AuthenticationPolicy] used to configure OAuth2 settings and verify JWT credentials.
 *
 * @return A Koin [Module] containing the authentication infrastructure definitions.
 */
fun authenticationModule() =
  module {
    single { get<ApplicationConfig>().toAuthConfig() }
    single { get<ApplicationConfig>().toSessionConfig() }

    single {
      val config = get<AuthConfig>()
      val client = get<HttpClient>()
      runBlocking {
        client.get(config.discoveryUrl).body<OpenIDDiscovery>()
      }
    }

    single {
      TokenProvider(
        httpClient = get(),
        discovery = get(),
        authConfig = get(),
      )
    } bind IdentityProvider::class

    single { UserManager(catalog = get()) }

    single {
      SessionManager(
        catalog = get(),
        userManager = get(),
        tokenProvider = get(),
      )
    }

    single {
      AuthenticationPolicy(authConfig = get(), discovery = get())
    }
  }
