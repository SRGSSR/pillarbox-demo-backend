package ch.srgssr.pillarbox.backend.application.auth

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.model.Role.Companion.toRole
import com.auth0.jwt.interfaces.Payload

/**
 * Extracts the preferred username from the JWT [Payload].
 * Usually contains the human-readable login name or display handle.
 */
val Payload.displayName: String
  get() =
    getClaim("name")?.asString() ?: subject

/**
 * Extracts Pillarbox [Role]s from a JWT [Payload] by reading the `roles` claim.
 *
 * @return an empty set when the claim is absent or contains no recognisable role names.
 */
val Payload.roles: Set<Role>
  get() =
    getClaim("roles")
      ?.asList(String::class.java)
      ?.mapNotNull { it.toRole() }
      ?.toSet() ?: emptySet()
