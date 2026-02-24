package ch.srgssr.pillarbox.backend.db

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.ApplicationConfigurationException

/**
 * Configuration parameters for the database connection pool (HikariCP).
 *
 * @property driverClassName The fully qualified name of the JDBC driver class (e.g., "org.postgresql.Driver").
 * @property jdbcUrl The connection string for the database.
 * @property username Database authentication username.
 * @property password Database authentication password.
 * @property poolSize Maximum number of connections in the pool. Defaults to 10.
 * @property connectionTimeout Maximum time (in ms) to wait for a connection from the pool. Defaults to 30s.
 * @property idleTimeout Maximum time (in ms) a connection is allowed to sit idle in the pool. Defaults to 10m.
 * @property dataSourceProperties Additional vendor-specific properties passed to the JDBC driver.
 */
data class DatabaseConfig(
  val autoCreate: Boolean = false,
  val driverClassName: String,
  val jdbcUrl: String,
  val username: String,
  val password: String,
  val poolSize: Int = 10,
  val connectionTimeout: Long = 30000,
  val idleTimeout: Long = 600000,
  val dataSourceProperties: Map<String, String> = emptyMap(),
)

/**
 * Maps an [ApplicationConfig] to a [DatabaseConfig] instance.
 *
 * It expects a `database` block in the configuration. e.g.:
 * ```hocon
 * database {
 *    driverClassName = "org.postgresql.Driver"
 *    jdbcUrl = "jdbc:postgresql://localhost:5432/db"
 *    username = "user"
 *    password = "password"
 *    properties {
 *      ssl = "true"
 *    }
 * }
 * ```
 *
 * @throws ApplicationConfigurationException If required fields are missing.
 *
 * @return A populated [DatabaseConfig].
 */
fun ApplicationConfig.toDatabaseConfig(): DatabaseConfig {
  val db = config("database")
  val customProps = mutableMapOf<String, String>()

  db.configOrNull("properties")?.keys()?.forEach { key ->
    customProps[key] = db.property("properties.$key").getString()
  }

  return DatabaseConfig(
    autoCreate = db.property("autoCreate").getString().toBoolean(),
    driverClassName = db.property("driverClassName").getString(),
    jdbcUrl = db.property("jdbcUrl").getString(),
    username = db.property("username").getString(),
    password = db.property("password").getString(),
    poolSize = db.propertyOrNull("poolSize")?.getString()?.toInt() ?: 10,
    connectionTimeout = db.propertyOrNull("connectionTimeout")?.getString()?.toLong() ?: 30000,
    idleTimeout = db.propertyOrNull("idleTimeout")?.getString()?.toLong() ?: 60000,
    dataSourceProperties = customProps,
  )
}

/**
 * Safely retrieves a sub-configuration block.
 *
 * @param path The configuration path to retrieve.
 *
 * @return The [ApplicationConfig] at the path, or `null` if the path does not exist.
 */
fun ApplicationConfig.configOrNull(path: String): ApplicationConfig? =
  try {
    config(path)
  } catch (_: ApplicationConfigurationException) {
    null
  }
