package ch.srgssr.pillarbox.backend.auth

import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.log.error
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Retrieves user profile data from the OIDC UserInfo endpoint and keeps the local
 * [User] record in sync with the identity provider.
 *
 * @property userRepository The persistent storage for user data.
 * @property httpClient The client used to call the OIDC UserInfo endpoint.
 * @property discovery The OIDC discovery document, used to resolve the UserInfo endpoint URL.
 */
class UserInfoProvider(
  private val userRepository: UserRepository,
  private val httpClient: HttpClient,
  private val discovery: OpenIDDiscovery,
) {
  companion object {
    val logger = logger()
  }

  /**
   * Fetches the user's profile from the OIDC UserInfo endpoint and synchronises the local
   * [User] record. Creates the record if it does not yet exist.
   *
   * @param accessToken The bearer token to authenticate the UserInfo request.
   * @param updateLastLogin Whether to update [User.lastLoginAt] to the current time.
   *                        Should be `true` on login, `false` on background re-validation.
   * @return The up-to-date [User], or `null` if the token is invalid or the endpoint is unreachable.
   */
  suspend fun fetchAndSync(
    accessToken: String,
    updateLastLogin: Boolean = false,
  ): User? = fetchUserInfo(accessToken)?.let { syncUser(it, updateLastLogin) }

  /**
   * Calls the OIDC UserInfo endpoint and deserialises the response.
   *
   * @param accessToken The bearer token used to authenticate the request.
   * @return The parsed [OidcUserInfo] when the provider returns 200 OK; `null` otherwise.
   */
  private suspend fun fetchUserInfo(accessToken: String): OidcUserInfo? =
    try {
      httpClient.get(discovery.userInfoEndpoint) { bearerAuth(accessToken) }.let { response ->
        logger.info { "UserInfo endpoint returned status: ${response.status}" }
        if (response.status == HttpStatusCode.OK) response.body<OidcUserInfo>() else null
      }
    } catch (e: IOException) {
      logger.error(e) { "Failed to reach OIDC UserInfo endpoint at ${discovery.userInfoEndpoint}" }
      null
    }

  /**
   * Creates or updates the local [User] record from [OidcUserInfo].
   *
   * If a record for [OidcUserInfo.sub] already exists, its [User.displayName] and
   * [User.updatedAt] are refreshed. [User.lastLoginAt] is only updated when
   * [updateLastLogin] is `true`.
   *
   * @param userInfo The profile received from the OIDC UserInfo endpoint.
   * @param updateLastLogin Whether to refresh [User.lastLoginAt].
   * @return The persisted [User].
   */
  private suspend fun syncUser(
    userInfo: OidcUserInfo,
    updateLastLogin: Boolean,
    now: Instant = Clock.System.now(),
  ): User =
    (
      userRepository.findByOidcSub(userInfo.sub)?.let {
        it.copy(
          displayName = userInfo.displayName,
          updatedAt = now,
          lastLoginAt = if (updateLastLogin) now else it.lastLoginAt,
        )
      } ?: User(
        oidcSub = userInfo.sub,
        displayName = userInfo.displayName,
        createdAt = now,
        updatedAt = now,
        lastLoginAt = now,
      )
    ).also { userRepository.save(it) }
}
