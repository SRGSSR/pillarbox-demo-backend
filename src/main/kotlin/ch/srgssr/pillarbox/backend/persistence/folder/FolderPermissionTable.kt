package ch.srgssr.pillarbox.backend.persistence.folder

import ch.srgssr.pillarbox.backend.persistence.team.TeamTable
import ch.srgssr.pillarbox.backend.persistence.user.UserTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Exposed table definition for folder access grants.
 *
 * Each row carries exactly one subject ([oidcSub], [teamId] or [role]); the others are
 * `null`. The exclusive-arc CHECK constraint is enforced by the SQL migration, while the
 * domain model guarantees it structurally. Deleting the referenced folder, user or team
 * cascades to this table.
 */
object FolderPermissionTable : Table("pb_folder_permission") {
  /** Unique identifier of the grant. */
  val id = varchar("id", 255)

  /** The folder this grant is attached to. */
  val folderId = varchar("folder_id", 255).references(FolderTable.id, onDelete = ReferenceOption.CASCADE)

  /** OIDC sub of the granted user, or `null` for team and role grants. */
  val oidcSub = varchar("oidc_sub", 255).references(UserTable.oidcSub, onDelete = ReferenceOption.CASCADE).nullable()

  /** Identifier of the granted team, or `null` for user and role grants. */
  val teamId = varchar("team_id", 255).references(TeamTable.id, onDelete = ReferenceOption.CASCADE).nullable()

  /** App role key of the granted role, or `null` for user and team grants. */
  val role = varchar("role", 255).nullable()

  /** Whether the grant confers write access to its subject. */
  val canWrite = bool("can_write")

  /** Timestamp of the grant creation. */
  val createdAt = timestampWithTimeZone("created_at")

  override val primaryKey = PrimaryKey(id)

  // Uniqueness of (folder_id, oidc_sub, team_id, role) is enforced by the
  // `UNIQUE NULLS NOT DISTINCT` constraint in V7__Add_Folder_Permissions.sql
}
