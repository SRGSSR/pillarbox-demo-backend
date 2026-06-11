package ch.srgssr.pillarbox.backend.persistence.session

import ch.srgssr.pillarbox.backend.db.EncryptionService
import ch.srgssr.pillarbox.backend.domain.model.Session
import ch.srgssr.pillarbox.backend.test.seedSession
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.testDatabaseConfig
import ch.srgssr.pillarbox.backend.test.testDb
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class SessionRepositoryTest :
  ShouldSpec({
    val encryptionService = EncryptionService(testDatabaseConfig)
    val repository = SessionRepository(testDb, encryptionService)

    should("save, find and delete a session by its stored id") {
      testApplicationContext {
        val session =
          seedSession(
            Session(
              sessionId = "stored-session-key",
              accessToken = "access-token",
              refreshToken = "refresh-token",
              oidcSub = "test-sub",
            ),
          )

        val found = repository.find(session.sessionId)

        found.shouldNotBeNull()
        found.sessionId shouldBe session.sessionId
        found.accessToken shouldBe "access-token"
        found.refreshToken shouldBe "refresh-token"

        repository.exists(session.sessionId) shouldBe true
        repository.delete(session.sessionId) shouldBe true
        repository.find(session.sessionId).shouldBeNull()
      }
    }

    should("store the tokens encrypted at rest") {
      testApplicationContext {
        val session =
          seedSession(
            Session(
              sessionId = "stored-session-key",
              accessToken = "secret-access-token",
              refreshToken = "secret-refresh-token",
              idToken = "secret-id-token",
              oidcSub = "test-sub",
            ),
          )

        val row =
          transaction(testDb) {
            SessionTable.selectAll().single()
          }

        row[SessionTable.sessionId] shouldBe session.sessionId
        row[SessionTable.publicId] shouldBe session.publicId

        row[SessionTable.accessToken] shouldNotBe "secret-access-token"
        row[SessionTable.refreshToken] shouldNotBe "secret-refresh-token"
        row[SessionTable.idToken] shouldNotBe "secret-id-token"

        encryptionService.decrypt(row[SessionTable.accessToken]) shouldBe "secret-access-token"
      }
    }
  })
