package ch.srgssr.pillarbox.backend.adapter.web.api.dto

import ch.srgssr.pillarbox.backend.domain.model.Session
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * API response representation of a user session for the V1 endpoint.
 *
 * Sessions are addressed by their public handle; the session identifier and
 * token material are credentials and never exposed.
 *
 * @property publicId Public handle of the session, independent of the session identifier.
 * @property oidcSub The OIDC sub of the user owning this session.
 * @property expiresAt Timestamp when the session expires.
 */
@Serializable
data class SessionResponseV1(
  val publicId: String,
  val oidcSub: String,
  val expiresAt: Instant,
)

/**
 * Converts a [Session] domain model to its V1 API response representation.
 *
 * @return A [SessionResponseV1] exposing the public handle and no credential material.
 */
fun Session.toSessionResponseV1() =
  SessionResponseV1(
    publicId = this.publicId,
    oidcSub = this.oidcSub,
    expiresAt = this.expiresAt,
  )
