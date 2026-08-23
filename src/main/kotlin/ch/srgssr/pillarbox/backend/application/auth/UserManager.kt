package ch.srgssr.pillarbox.backend.application.auth

import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.domain.port.UserCatalog
import com.auth0.jwt.interfaces.Payload

/**
 * Manages user persistence based on OIDC authentication data.
 *
 * @param catalog The port used to store and retrieve [User] entities.
 */
class UserManager(
  private val catalog: UserCatalog,
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
    catalog.save(
      User(
        oidcSub = payload.subject,
        displayName = payload.displayName,
        roles = payload.roles,
      ),
    )
  }
}
