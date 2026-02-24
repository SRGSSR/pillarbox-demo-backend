package ch.srgssr.pillarbox.backend.db

import org.flywaydb.core.Flyway
import javax.sql.DataSource

/**
 * Executes database schema migrations using Flyway.e
 *
 * This extension function initializes Flyway with the current [DataSource] and
 * applies all pending migration scripts found in the default location (`db/migration`).
 *
 * @throws org.flywaydb.core.api.FlywayException If the migration fails.
 */
fun DataSource.runMigration() {
  Flyway
    .configure()
    .dataSource(this)
    .load()
    .migrate()
}
