package ch.srgssr.pillarbox.backend.entrypoint.web.dto

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.model.User
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * API response representation of a user for the V1 endpoint.
 *
 * @property oidcSub The `sub` (subject) claim from the OpenID Connect identity provider.
 * @property displayName Human-readable name of the user.
 * @property roles The roles held by this user.
 * @property createdAt Timestamp when the user record was created.
 * @property updatedAt Timestamp of the last user record update.
 */
@Serializable
data class UserResponseV1(
  val oidcSub: String,
  val displayName: String,
  val roles: Set<Role> = emptySet(),
  val createdAt: Instant,
  val updatedAt: Instant,
)

/**
 * Converts a [User] domain model to its V1 API response representation.
 *
 * @return A [UserResponseV1] containing the domain model's data.
 */
fun User.toUserResponseV1() =
  UserResponseV1(
    oidcSub = this.oidcSub,
    displayName = this.displayName,
    roles = this.roles,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
  )
