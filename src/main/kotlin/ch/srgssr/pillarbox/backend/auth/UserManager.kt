package ch.srgssr.pillarbox.backend.auth

import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import com.auth0.jwt.interfaces.Payload

/**
 * Manages user persistence based on OIDC authentication data.
 *
 * @param repository The repository used to store and retrieve [User] entities.
 */
class UserManager(
  private val repository: UserRepository,
) {
  /**
   * Inserts or updates a [User] from the given JWT [payload].
   *
   * The user's [OIDC subject][Payload.getSubject] is used as the unique identifier,
   * and the [display name][Payload.displayName] is stored alongside it.
   *
   * @param payload The decoded JWT payload from the authenticated request.
   */
  suspend fun upsert(payload: Payload) {
    repository.save(
      User(
        oidcSub = payload.subject,
        displayName = payload.displayName,
        roles = payload.roles,
      ),
    )
  }
}
