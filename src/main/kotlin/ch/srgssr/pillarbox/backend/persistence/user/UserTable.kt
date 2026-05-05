package ch.srgssr.pillarbox.backend.persistence.user

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Exposed table definition for persisting user accounts.
 *
 * This table stores identity information for users authenticated via OIDC.
 */
object UserTable : Table("pb_user") {
  /**
   * The OIDC subject claim (`sub`) that uniquely identifies the user at the identity provider.
   */
  val oidcSub = varchar("oidc_sub", 255)

  /**
   * Human-readable name for the user, derived from their OIDC profile.
   */
  val displayName = varchar("display_name", 255)

  /**
   * The time when the user record was last updated.
   */
  val updatedAt = timestampWithTimeZone("updated_at")

  /**
   * The time when the user record was created.
   */
  val createdAt = timestampWithTimeZone("created_at")

  /**
   * Primary key definition using the [id] column.
   */
  override val primaryKey = PrimaryKey(oidcSub)
}
