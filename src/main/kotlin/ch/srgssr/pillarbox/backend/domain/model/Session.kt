package ch.srgssr.pillarbox.backend.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents an authenticated user session within the Pillarbox backend.
 *
 * @property sessionId A unique identifier for the session. Defaults to a random UUID string.
 * @property accessToken The access token used for bearer authentication against downstream services.
 * @property lastChecked The [Instant] representing the last time this session was successfully
 *                       validated against the identity provider. Defaults to the current system time.
 * @property expiresAt The [Instant] representing the absolute hard expiration of the seassion.
 * @property userId The identifier of the [ch.srgssr.pillarbox.backend.domain.model.User] associated
 *                  with this session, or `null` if no user record has been linked yet.
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class Session(
  val sessionId: String = Uuid.random().toString(),
  val accessToken: String,
  val refreshToken: String? = null,
  val idToken: String? = null,
  val lastChecked: Instant = Clock.System.now(),
  val expiresAt: Instant = lastChecked + 24.hours,
  val userId: String? = null,
) {
  /**
   * Returns `true` if the current system time has surpassed the [expiresAt] threshold.
   */
  val expired: Boolean get() = expiresAt < Clock.System.now()
}
