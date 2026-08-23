package ch.srgssr.pillarbox.backend.test

import ch.srgssr.pillarbox.backend.adapter.web.api.Navigation
import ch.srgssr.pillarbox.backend.domain.model.Role
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.koin.core.context.GlobalContext

val writeRoles: Set<Role> = setOf(Role.WRITE)

/**
 * Executes an integration test within a managed Ktor application environment.
 *
 * This utility automates the integration testing by:
 *    1. Loading the standard `application.conf` and pointing the database at the shared
 *       [TestDatabase] PostgreSQL container, so the real Flyway migrations are exercised.
 *    2. Providing a pre-configured [HttpClient] with JSON support for API calls.
 *    3. Overriding `oidc.issuer` in the config to point to the mock server.
 *    4. Ensuring database isolation between tests by truncating all tables after each
 *       test execution.
 *
 * @param configOverrides Additional configuration properties applied on top of the defaults.
 * @param block The test logic to execute, provides access to the [ApplicationTestBuilder]
 *              and a pre-configured `client` with JSON support.
 */
fun testApplicationContext(
  enableProxyHeaders: Boolean = false,
  configOverrides: Map<String, String> = emptyMap(),
  block: suspend ApplicationTestBuilder.() -> Unit,
) {
  val oAuthServer = mockOAuth2Server().also { it.start() }

  testApplication {
    configure("application.conf") {
      this["ktor.deployment.enable_forwarded_headers"] = enableProxyHeaders.toString()
      this["database.driverClassName"] = "org.postgresql.Driver"
      this["database.jdbcUrl"] = TestDatabase.container.jdbcUrl
      this["database.username"] = TestDatabase.container.username
      this["database.password"] = TestDatabase.container.password
      this["oidc.issuer"] = oAuthServer.issuerUrl("pillarbox-realm").toString()
      this["oidc.discovery_path"] = ".well-known/openid-configuration"
      this["oidc.client_id"] = "pillarbox-test-client"
      this["oidc.realm"] = "pillarbox-realm"
      configOverrides.forEach { (key, value) -> this[key] = value }
    }

    client =
      createClient {
        followRedirects = false
        install(HttpCookies)
        install(ContentNegotiation) {
          json(
            Json {
              explicitNulls = false
            },
          )
        }
      }

    try {
      application.attributes.put(MockServerKey, oAuthServer)
      block()
    } finally {
      TestDatabase.truncateAll()
      oAuthServer.shutdown()
    }
  }
}

private val MockServerKey = AttributeKey<MockOAuth2Server>("MockOAuth2Server")

val ApplicationTestBuilder.mockServer: MockOAuth2Server
  get() =
    application.attributes.getOrNull(MockServerKey)
      ?: error("MockOAuth2Server not found in application attributes.")

val ApplicationTestBuilder.token: String
  get() = tokenWithRoles(writeRoles)

fun ApplicationTestBuilder.tokenWithRoles(roles: Set<Role>): String =
  mockServer
    .issueToken(
      issuerId = "pillarbox-realm",
      audience = "pillarbox-test-client",
      claims = mapOf("roles" to roles.map { it.key }),
    ).serialize()

fun ApplicationTestBuilder.tokenFor(
  subject: String,
  roles: Set<Role> = writeRoles,
): String =
  mockServer
    .issueToken(
      issuerId = "pillarbox-realm",
      clientId = "pillarbox-test-client",
      tokenCallback =
        DefaultOAuth2TokenCallback(
          issuerId = "pillarbox-realm",
          subject = subject,
          audience = listOf("pillarbox-test-client"),
          claims = mapOf("roles" to roles.map { it.key }),
        ),
    ).serialize()

suspend fun ApplicationTestBuilder.login(roles: Set<Role> = writeRoles): HttpResponse {
  mockServer.enqueueCallback(
    DefaultOAuth2TokenCallback(
      issuerId = "pillarbox-realm",
      subject = "dev-user",
      claims = mapOf("roles" to roles.map { it.key }),
    ),
  )

  val loginResponse = client.get(Navigation.LOGIN)
  val authorizationUrl =
    loginResponse.headers[HttpHeaders.Location]
      ?: error("Expected a redirect to the OAuth authorization endpoint")

  val oauthClient = GlobalContext.get().get<HttpClient>().config { followRedirects = false }
  val authResponse = oauthClient.use { it.get(authorizationUrl) }
  val callbackRedirect =
    authResponse.headers[HttpHeaders.Location]
      ?: error("Expected mock OAuth server to redirect back with a code")

  val code = callbackRedirect.substringAfter("code=").substringBefore("&")
  val state = callbackRedirect.substringAfter("state=").substringBefore("&")

  return client.get(Navigation.CALLBACK) {
    url {
      parameters.append("code", code)
      parameters.append("state", state)
    }
  }
}
