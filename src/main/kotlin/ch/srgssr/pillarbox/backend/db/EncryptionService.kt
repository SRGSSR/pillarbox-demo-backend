package ch.srgssr.pillarbox.backend.db

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_LENGTH_BYTES = 12
private const val TAG_LENGTH_BITS = 128

/**
 * Encrypts and hashes credential values so they are stored unreadable at rest,
 * while remaining usable by the application.
 *
 * Encryption uses AES-256-GCM with a key derived from [DatabaseConfig.encryptionKey],
 * which is held by the application only — values in the database cannot be read with
 * database access alone.
 *
 * @param config The database configuration providing the encryption key.
 */
class EncryptionService(
  config: DatabaseConfig,
) {
  private val key =
    SecretKeySpec(
      MessageDigest.getInstance("SHA-256").digest(config.encryptionKey.toByteArray()),
      "AES",
    )
  private val random = SecureRandom()

  /**
   * Encrypts a value with AES-256-GCM using a random IV per call.
   *
   * @param value The plaintext to encrypt.
   * @return A Base64 string containing the IV followed by the ciphertext and authentication tag.
   */
  fun encrypt(value: String): String {
    val iv = ByteArray(IV_LENGTH_BYTES).also { random.nextBytes(it) }
    val cipher =
      Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
      }

    return Base64.getEncoder().encodeToString(iv + cipher.doFinal(value.toByteArray()))
  }

  /**
   * Decrypts a value previously produced by [encrypt].
   *
   * @param value The Base64 string containing the IV, ciphertext and authentication tag.
   * @return The decrypted plaintext.
   */
  fun decrypt(value: String): String {
    val bytes = Base64.getDecoder().decode(value)
    val cipher =
      Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH_BYTES))
      }

    return String(cipher.doFinal(bytes, IV_LENGTH_BYTES, bytes.size - IV_LENGTH_BYTES))
  }

  /**
   * Hashes a value with SHA-256, for credentials that are only ever compared, never read back.
   *
   * @param value The value to hash.
   * @return The digest as a lowercase hex string.
   */
  @OptIn(ExperimentalStdlibApi::class)
  fun hash(value: String): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(value.toByteArray())
      .toHexString()
}
