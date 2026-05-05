package ch.srgssr.pillarbox.backend.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the response from the OIDC UserInfo endpoint.
 *
 * @property sub The subject identifier — the unique, stable identifier for the user at the identity provider.
 * @property name The user's full display name, if provided by the identity provider.
 * @property preferredUsername The user's preferred username, used as a fallback display name.
 */
@Serializable
data class OidcUserInfo(
  val sub: String,
  val name: String? = null,
  @SerialName("preferred_username") val preferredUsername: String? = null,
) {
  /**
   * The best available display name, falling back from [name] to [preferredUsername] to [sub].
   */
  val displayName: String get() = name ?: preferredUsername ?: sub
}
