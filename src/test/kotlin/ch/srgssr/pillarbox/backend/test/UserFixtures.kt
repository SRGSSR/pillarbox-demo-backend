package ch.srgssr.pillarbox.backend.test

import ch.srgssr.pillarbox.backend.adapter.persistence.DatabaseConfig
import ch.srgssr.pillarbox.backend.adapter.persistence.EncryptionService
import ch.srgssr.pillarbox.backend.adapter.persistence.session.SessionRepository
import ch.srgssr.pillarbox.backend.adapter.persistence.user.UserRepository
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.domain.model.User
import io.ktor.server.testing.ApplicationTestBuilder
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Connection to the same [TestDatabase] PostgreSQL container used by the application under test.
 *
 * The Ktor test engine loads application classes in a child classloader, so repository
 * instances obtained from the application's Koin context cannot be used directly from
 * test code. Seeding goes through this dedicated connection instead, pointed at the same
 * container so it shares the schema and data with the application.
 */
val testDatabaseConfig by lazy {
  DatabaseConfig(
    driverClassName = "org.postgresql.Driver",
    jdbcUrl = TestDatabase.container.jdbcUrl,
    username = TestDatabase.container.username,
    password = TestDatabase.container.password,
    encryptionKey = "test-encryption-key-32-chars-long!!",
  )
}

val testDb by lazy {
  with(testDatabaseConfig) {
    Database.connect(url = jdbcUrl, driver = driverClassName, user = username, password = password)
  }
}

fun userFixture(
  oidcSub: String = "test-user",
  displayName: String = "Test User",
  roles: Set<Role> = emptySet(),
) = User(
  oidcSub = oidcSub,
  displayName = displayName,
  roles = roles,
)

suspend fun ApplicationTestBuilder.seedUser(user: User = userFixture()): User {
  startApplication()
  return UserRepository(testDb).save(user)
}

suspend fun ApplicationTestBuilder.seedSession(session: Session): Session {
  startApplication()
  return SessionRepository(testDb, EncryptionService(testDatabaseConfig)).save(session)
}
