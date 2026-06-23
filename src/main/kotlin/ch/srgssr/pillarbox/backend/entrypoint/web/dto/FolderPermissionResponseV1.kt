package ch.srgssr.pillarbox.backend.entrypoint.web.dto

import ch.srgssr.pillarbox.backend.domain.model.FolderPermission
import ch.srgssr.pillarbox.backend.domain.model.PermissionSubject
import ch.srgssr.pillarbox.backend.domain.model.Role
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * API response representation of a folder access grant for the V1 endpoint.
 *
 * Exactly one of [oidcSub], [teamId] or [role] is set. The [folderId] identifies the
 * folder the grant is attached to, which for inherited grants is an ancestor of the
 * requested folder.
 *
 * @property id Unique identifier of the grant.
 * @property folderId The folder the grant is attached to.
 * @property oidcSub The OIDC sub of the granted user, if a user grant.
 * @property teamId The ID of the granted team, if a team grant.
 * @property role The granted role, if a role grant.
 * @property canWrite Whether the grant confers write access to its subject.
 * @property createdAt Timestamp of the grant creation.
 */
@Serializable
data class FolderPermissionResponseV1(
  val id: String,
  val folderId: String,
  val oidcSub: String? = null,
  val teamId: String? = null,
  val role: Role? = null,
  val canWrite: Boolean,
  val createdAt: Instant,
)

/**
 * Converts a [FolderPermission] domain model to its V1 API response representation.
 *
 * @return A [FolderPermissionResponseV1] containing the domain model's data.
 */
fun FolderPermission.toFolderPermissionResponseV1() =
  FolderPermissionResponseV1(
    id = this.id,
    folderId = this.folderId,
    oidcSub = (this.subject as? PermissionSubject.ForUser)?.oidcSub,
    teamId = (this.subject as? PermissionSubject.ForTeam)?.teamId,
    role = (this.subject as? PermissionSubject.ForRole)?.role,
    canWrite = this.canWrite,
    createdAt = this.createdAt,
  )
