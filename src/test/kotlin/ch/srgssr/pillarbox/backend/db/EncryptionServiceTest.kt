package ch.srgssr.pillarbox.backend.db

import ch.srgssr.pillarbox.backend.test.testDatabaseConfig
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch

class EncryptionServiceTest :
  ShouldSpec({
    val service = EncryptionService(testDatabaseConfig)

    should("decrypt what it encrypted") {
      val plaintext = "a-very-secret-access-token"

      service.decrypt(service.encrypt(plaintext)) shouldBe plaintext
    }

    should("produce different ciphertexts for the same plaintext") {
      val plaintext = "a-very-secret-access-token"

      service.encrypt(plaintext) shouldNotBe service.encrypt(plaintext)
    }

    should("not decrypt with a different key") {
      val other = EncryptionService(testDatabaseConfig.copy(encryptionKey = "another-encryption-key-32-chars!!"))

      shouldThrowAny {
        other.decrypt(service.encrypt("a-very-secret-access-token"))
      }
    }

    should("fail to decrypt tampered ciphertext") {
      val encrypted = service.encrypt("a-very-secret-access-token")
      val tampered = encrypted.dropLast(4) + "AAA="

      shouldThrowAny {
        service.decrypt(tampered)
      }
    }

    should("hash deterministically to a hex digest") {
      service.hash("session-id") shouldBe service.hash("session-id")
      service.hash("session-id") shouldNotBe service.hash("other-session-id")
      service.hash("session-id") shouldMatch Regex("[0-9a-f]{64}")
    }
  })
