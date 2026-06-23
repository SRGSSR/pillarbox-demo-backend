package ch.srgssr.pillarbox.backend.test

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Shared PostgreSQL container backing the test suite.
 *
 * A single container is started lazily on first use and reused across every test, so the suite
 * exercises the real Flyway migrations and PostgreSQL production behaviour. Per-test isolation
 * is achieved by [truncateAll] rather than by recreating the schema.
 *
 * Requires a running Docker daemon.
 */
object TestDatabase {
  val container: PostgreSQLContainer<*> by lazy {
    PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).apply { start() }
  }

  /**
   * Empties every data table in the `public` schema, leaving the migrated schema and Flyway
   * history intact so the next test starts from a clean slate.
   */
  fun truncateAll() {
    container.createConnection("").use { connection ->
      connection.createStatement().use { statement ->
        val tables = mutableListOf<String>()
        statement
          .executeQuery(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
          ).use { rows ->
            while (rows.next()) {
              tables += rows.getString(1)
            }
          }

        if (tables.isNotEmpty()) {
          statement.execute("TRUNCATE TABLE ${tables.joinToString(", ") { "\"$it\"" }} RESTART IDENTITY CASCADE")
        }
      }
    }
  }
}
