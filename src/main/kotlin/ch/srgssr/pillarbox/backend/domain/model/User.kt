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
 * @property roles The list of roles of this user.
 * @property createdAt Timestamp of the initial creation of this user record.
 * @property updatedAt Timestamp of the last modification to this user record.
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class User(
  val oidcSub: String,
  val displayName: String,
  val roles: Set<Role> = emptySet(),
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

  fun hasAnyRole(required: Set<Role>): Boolean = roles.any { it in required }
}

/**
 * Defines the access roles for the Pillarbox Demo backend.
 *
 * Each entry maps to an app role using the `PillarboxDemo.{Level}` naming convention
 * (e.g., `PillarboxDemo.Read`).
 */
enum class Role {
  /** Read-only access. */
  READ,

  /** Read and write access. */
  WRITE,

  /** Full administrative access. */
  ADMIN,

  ;

  /** The app role key, following the `PillarboxDemo.{Level}` convention. */
  val key = "PillarboxDemo.${name.lowercase().replaceFirstChar { it.uppercase() }}"

  companion object {
    /**
     * Finds a [Role] entry matching the given app role [key].
     *
     * @param key The app role key (e.g., `PillarboxDemo.Read`).
     *
     * @return The matching [Role], or `null` if no match is found.
     */
    fun find(key: String) = entries.find { it.key == key }

    /**
     * Parses this string into a [Role], or returns `null` if unrecognised.
     */
    fun String.toRole(): Role? = find(this)
  }
}
