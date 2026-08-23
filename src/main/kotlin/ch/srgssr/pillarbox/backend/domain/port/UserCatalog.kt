package ch.srgssr.pillarbox.backend.domain.port

import ch.srgssr.pillarbox.backend.domain.catalog.PaginatedResult
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.model.User

/**
 * Reads and writes the user directory.
 */
interface UserCatalog {
  /**
   * Finds a user by OIDC subject.
   *
   * @param oidcSub The OIDC subject of the user.
   * @return The user, or `null` if it does not exist.
   */
  suspend fun find(oidcSub: String): User?

  /**
   * Whether a user exists with this OIDC subject.
   *
   * @param oidcSub The OIDC subject of the user.
   * @return `true` if it exists, `false` otherwise.
   */
  suspend fun exists(oidcSub: String): Boolean

  /**
   * Persists or updates a user record.
   *
   * @param item The user to save.
   * @return The persisted user.
   */
  suspend fun save(item: User): User

  /**
   * Retrieves a page of users, most recently updated first.
   *
   * @param text Optional filter on the display name; `null` or blank lists everyone.
   * @param slice The window of the result to return.
   * @return A [PaginatedResult] with the matching users and the total number of matches.
   */
  suspend fun page(
    text: String? = null,
    slice: QuerySlice = QuerySlice(),
  ): PaginatedResult<User>

  /**
   * Retrieves the users matching the given OIDC subjects; unknown subjects are absent.
   *
   * @param oidcSubs The OIDC subjects to look up.
   * @return The existing users among [oidcSubs].
   */
  suspend fun list(oidcSubs: Collection<String>): List<User>
}
