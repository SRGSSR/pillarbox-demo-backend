package ch.srgssr.pillarbox.backend.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module

/**
 * Defines the Koin module for authentication and session management.
 *
 * This module provides:
 * 1. The [AuthConfig] and [SessionConfig] extracted from the application configuration.
 * 2. The [OpenIDDiscovery] document, fetched synchronously from the identity provider's
 * discovery URL. Note: This involves a [runBlocking] network call upon first resolution.
 * 3. A [SessionManager] to handle user session lifecycles and token validation.
 * 4. An [AuthenticationPolicy] used to configure OAuth2 settings and verify JWT credentials.
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
      SessionManager(
        repository = get(),
        httpClient = get(),
        userInfoUrl = get<AuthConfig>().userInfoUrl,
        validationIntervalSeconds = get<SessionConfig>().validationIntervalSeconds,
      )
    }

    single {
      val auth = get<AuthConfig>()

      AuthenticationPolicy(
        clientId = auth.clientId,
        clientSecret = auth.clientSecret,
        discovery = get(),
      )
    }
  }
