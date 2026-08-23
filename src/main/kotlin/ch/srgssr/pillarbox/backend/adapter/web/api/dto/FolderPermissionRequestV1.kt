package ch.srgssr.pillarbox.backend.adapter.web.api.dto

import ch.srgssr.pillarbox.backend.domain.model.FolderPermission
import ch.srgssr.pillarbox.backend.domain.model.PermissionSubject
import ch.srgssr.pillarbox.backend.domain.model.Role
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (V1) representing a request to grant folder access to a subject.
 *
 * Exactly one of [oidcSub], [teamId] or [role] must be provided.
 *
 * @property oidcSub The OIDC sub of the user to grant access to.
 * @property teamId The ID of the team to grant access to.
 * @property role The role to grant access to.
 * @property canWrite Whether the grant confers write access to its subject.
 */
@Serializable
data class FolderPermissionRequestV1(
  val oidcSub: String? = null,
  val teamId: String? = null,
  val role: Role? = null,
  val canWrite: Boolean = true,
) {
  /**
   * Maps the [FolderPermissionRequestV1] DTO to the internal [FolderPermission] domain model.
   *
   * @param folderId The folder the grant is attached to.
   * @return A [FolderPermission] instance, or `null` if not exactly one subject was provided.
   */
  fun toFolderPermission(folderId: String): FolderPermission? {
    val subject =
      listOfNotNull(
        oidcSub?.let { PermissionSubject.ForUser(it) },
        teamId?.let { PermissionSubject.ForTeam(it) },
        role?.let { PermissionSubject.ForRole(it) },
      ).singleOrNull() ?: return null

    return FolderPermission(
      folderId = folderId,
      subject = subject,
      canWrite = canWrite,
    )
  }
}
