package ch.srgssr.pillarbox.backend.test

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.Navigation
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
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.context.GlobalContext

val writeRoles: Set<Role> = setOf(Role.WRITE)

/**
 * Executes an integration test within a managed Ktor application environment.
 *
 * This utility automates the integration testing by:
 *    1. Loading the standard `application.conf` to configure the server and database.
 *    2. Providing a pre-configured [HttpClient] with JSON support for API calls.
 *    3. Overriding `oidc.issuer` in the config to point to the mock server.
 *    4. Ensuring database isolation between tests by dropping and recreating all
 *       registered [Table] schemas after each test execution.
 *
 * @param block The test logic to execute, provides access to the [ApplicationTestBuilder]
 *              and a pre-configured `client` with JSON support.
 */
fun testApplicationContext(
  enableProxyHeaders: Boolean = false,
  block: suspend ApplicationTestBuilder.() -> Unit,
) {
  val oAuthServer = mockOAuth2Server().also { it.start() }

  testApplication {
    configure("application.conf") {
      this["ktor.deployment.enable_forwarded_headers"] = enableProxyHeaders.toString()
      this["oidc.issuer"] = oAuthServer.issuerUrl("pillarbox-realm").toString()
      this["oidc.discovery_path"] = ".well-known/openid-configuration"
      this["oidc.client_id"] = "pillarbox-test-client"
      this["oidc.realm"] = "pillarbox-realm"
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
      val allTables =
        GlobalContext
          .get()
          .get<List<Table>>()
          .toTypedArray()
      transaction {
        SchemaUtils.drop(*allTables)
        SchemaUtils.create(*allTables)
      }

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
