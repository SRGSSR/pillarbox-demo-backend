package ch.srgssr.pillarbox.backend.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

/**
 * Represents an authenticated user of the Pillarbox backend.
 *
 * A new instance is typically created at first login and updated on subsequent logins.
 *
 * @property oidcSub The `sub` (subject) claim from the OpenID Connect identity provider.
 * @property displayName Human-readable name shown in the UI (e.g. "Jane Doe").
 * @property createdAt Timestamp of the initial creation of this user record.
 * @property updatedAt Timestamp of the last modification to this user record.
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class User(
  val oidcSub: String,
  val displayName: String,
  val createdAt: Instant = Clock.System.now(),
  val updatedAt: Instant = Clock.System.now(),
) {
  /**
   * Up to two uppercase initials derived from the words in [displayName].
   * For example, "Jane Doe" → "JD", "Alice" → "A".
   */
  val initials: String
    get() =
      displayName
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
}
