package ch.srgssr.pillarbox.backend.test

import ch.srgssr.pillarbox.backend.db.DatabaseConfig
import ch.srgssr.pillarbox.backend.db.EncryptionService
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.persistence.session.SessionRepository
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import io.ktor.server.testing.ApplicationTestBuilder
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Connection to the same in-memory H2 database used by the application under test.
 *
 * The Ktor test engine loads application classes in a child classloader, so repository
 * instances obtained from the application's Koin context cannot be used directly from
 * test code. Seeding goes through this dedicated connection instead, configured to
 * match `src/test/resources/application.conf`.
 */
val testDatabaseConfig =
  DatabaseConfig(
    driverClassName = "org.h2.Driver",
    jdbcUrl = "jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    username = "sa",
    password = "",
    encryptionKey = "test-encryption-key-32-chars-long!!",
  )

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
