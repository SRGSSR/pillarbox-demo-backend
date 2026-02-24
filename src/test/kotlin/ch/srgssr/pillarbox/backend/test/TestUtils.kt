package ch.srgssr.pillarbox.backend.test

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
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
 *    3. Ensuring database isolation between tests by dropping and recreating all
 *       registered [Table] schemas after each test execution.
 *
 * @param block The test logic to execute, provides access to the [ApplicationTestBuilder]
 *              and a pre-configured `client` with JSON support.
 */
fun testApplicationContext(block: suspend ApplicationTestBuilder.() -> Unit) =
  testApplication {
    environment {
      config = ApplicationConfig("application.conf")
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
      block()
    } finally {
      val allTables =
        GlobalContext
          .get()
          .getAll<Table>()
          .toList()
          .toTypedArray()
      transaction {
        SchemaUtils.drop(*allTables)
        SchemaUtils.create(*allTables)
      }
    }
  }
