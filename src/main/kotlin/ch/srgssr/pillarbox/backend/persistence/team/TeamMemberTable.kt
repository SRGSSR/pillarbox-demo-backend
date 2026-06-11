package ch.srgssr.pillarbox.backend.persistence.team

import ch.srgssr.pillarbox.backend.persistence.user.UserTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Junction table linking users to teams.
 *
 * Each row represents a single team membership; a user may belong to several
 * teams. Deleting either the referenced [UserTable] or [TeamTable] row
 * cascades to this table.
 */
object TeamMemberTable : Table("pb_team_member") {
  /** Identifier of the team. */
  val teamId = varchar("team_id", 255).references(TeamTable.id, onDelete = ReferenceOption.CASCADE)

  /** OIDC sub of the member user. */
  val oidcSub = varchar("oidc_sub", 255).references(UserTable.oidcSub, onDelete = ReferenceOption.CASCADE)

  /** Timestamp when the user was added to the team. */
  val addedAt = timestampWithTimeZone("added_at")

  override val primaryKey = PrimaryKey(teamId, oidcSub)
}
