package ch.srgssr.pillarbox.backend.domain.model

import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents an authenticated user session within the Pillarbox backend.
 *
 * @property sessionId The unique identifier for the session: the SHA-256 hash of the raw
 *                     session id held in the user's cookie. The raw value is hashed at the
 *                     authentication layer and never enters the domain or persistence layers,
 *                     so the cookie credential cannot be recovered from the database. Still
 *                     treated as sensitive: never exposed through APIs, logs, or UI — use
 *                     [publicId] instead.
 * @property publicId A public handle for this session, independent of [sessionId]. Safe to expose
 *                    through APIs and to use as a lookup key, since it carries no information
 *                    about the session credentials.
 * @property accessToken The access token used for bearer authentication against downstream services.
 * @property expiresAt The [Instant] representing the absolute hard expiration of the session.
 * @property oidcSub The OIDC sub that identifies the [ch.srgssr.pillarbox.backend.domain.model.User] associated
 *                   with this session.
 */
@OptIn(ExperimentalUuidApi::class)
data class Session(
  val sessionId: String,
  val publicId: String = Uuid.random().toString(),
  val accessToken: String,
  val refreshToken: String? = null,
  val idToken: String? = null,
  val expiresAt: Instant = Clock.System.now() + 5.minutes,
  val oidcSub: String,
) {
  /**
   * Returns `true` if the current system time has surpassed the [expiresAt] threshold.
   */
  val expired: Boolean get() = expiresAt < Clock.System.now()

  /**
   * Redacts credential material ([sessionId] and tokens) so sessions can be
   * safely interpolated into log messages.
   *
   * @return A string containing only the [publicId], [oidcSub] and [expiresAt].
   */
  override fun toString(): String = "Session(publicId=$publicId, oidcSub=$oidcSub, expiresAt=$expiresAt)"
}
