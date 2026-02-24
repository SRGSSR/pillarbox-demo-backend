package ch.srgssr.pillarbox.backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.logger.Level.DEBUG
import org.koin.dsl.module
import javax.sql.DataSource

/**
 * Defines the Koin module for database infrastructure.
 *
 * This module provides:
 *   1. A [DataSource] (HikariCP) configured using the provided [dbConfig].
 *   2. An Exposed [Database] instance, which automatically triggers [runMigration] on the
 *      data source before establishing the connection.
 *
 * @param dbConfig The configuration parameters for the database.
 *
 * @return A Koin [Module] containing the database infrastructure definitions.
 */
fun databaseModule(dbConfig: DatabaseConfig) =
  module {
    single<DataSource> {
      HikariDataSource(
        HikariConfig().apply {
          driverClassName = dbConfig.driverClassName
          jdbcUrl = dbConfig.jdbcUrl
          username = dbConfig.username
          password = dbConfig.password

          maximumPoolSize = dbConfig.poolSize
          connectionTimeout = dbConfig.connectionTimeout
          idleTimeout = dbConfig.idleTimeout

          // Apply JDBC properties
          dbConfig.dataSourceProperties.forEach { (key, value) ->
            addDataSourceProperty(key, value)
          }

          validate()
        },
      )
    }

    single {
      val dataSource = get<DataSource>()
      val db = Database.connect(dataSource)

      if (dbConfig.autoCreate) {
        val allTables = getAll<Table>().toTypedArray()

        transaction(db) {
          SchemaUtils.create(*allTables)
        }
      } else {
        dataSource.runMigration()
      }
      db
    }
  }
