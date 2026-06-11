package ch.srgssr.pillarbox.backend.persistence.team

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Exposed table definition for persisting team metadata.
 *
 * Team membership is stored separately, see [TeamMemberTable].
 */
object TeamTable : Table("pb_team") {
  /** Unique identifier for the team. */
  val id = varchar("id", 255)

  /** Display name of the team, unique across all teams. */
  val name = varchar("name", 255).uniqueIndex()

  /** Timestamp when the team was created. */
  val createdAt = timestampWithTimeZone("created_at")

  /** Timestamp when the team was last updated. */
  val updatedAt = timestampWithTimeZone("updated_at")

  override val primaryKey = PrimaryKey(id)
}
