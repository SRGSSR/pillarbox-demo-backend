package ch.srgssr.pillarbox.backend.persistence.session

import ch.srgssr.pillarbox.backend.persistence.session.SessionTable.sessionId
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Exposed table definition for persisting authenticated user sessions.
 *
 * This table stores OAuth2/OIDC session tokens and their associated lifecycle timestamps.
 */
object SessionTable : Table("pb_session") {
  /**
   * Unique identifier for the session.
   */
  val sessionId = varchar("session_id", 255)

  /**
   * The access token used for bearer authentication against downstream services.
   */
  val accessToken = text("access_token")

  /**
   * The refresh token used to obtain a new access token, or `null` if not provided.
   */
  val refreshToken = text("refresh_token").nullable()

  /**
   * The ID token issued by the identity provider, or `null` if not provided.
   */
  val idToken = text("id_token").nullable()

  /**
   * The time when this session expires absolutely.
   */
  val expiresAt = timestampWithTimeZone("expires_at")

  /**
   * The OIDC sub that identifies the [ch.srgssr.pillarbox.backend.domain.model.User] associated
   * with this session.
   */
  val oidcSub = varchar("oidc_sub", 255)

  /**
   * Primary key definition using the [sessionId] column.
   */
  override val primaryKey = PrimaryKey(sessionId)
}
