package ch.srgssr.pillarbox.backend.test

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.context.GlobalContext

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
fun testApplicationContext(block: suspend ApplicationTestBuilder.(MockOAuth2Server) -> Unit) {
  val oAuthServer = MockOAuth2Server()
  oAuthServer.start()

  testApplication {
    configure("application.conf") {
      val issuerUrl = oAuthServer.issuerUrl("pillarbox-realm").toString()

      this["oidc.issuer"] = issuerUrl
      this["oidc.client_id"] = "pillarbox-test-client"
      this["oidc.realm"] = "pillarbox-realm"
    }

    client =
      createClient {
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
      block(oAuthServer)
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
  get() =
    mockServer
      .issueToken(
        issuerId = "pillarbox-realm",
        audience = "pillarbox-test-client",
      ).serialize()
