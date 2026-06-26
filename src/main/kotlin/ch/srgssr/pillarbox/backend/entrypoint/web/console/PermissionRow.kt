package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.domain.model.FolderPermission
import ch.srgssr.pillarbox.backend.domain.model.PermissionSubject
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.persistence.folder.FolderPermissionRepository
import ch.srgssr.pillarbox.backend.persistence.team.TeamRepository
import ch.srgssr.pillarbox.backend.persistence.team.TeamTable
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import ch.srgssr.pillarbox.backend.persistence.user.UserTable
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.inList

/**
 * A single row rendered in the permissions dialog.
 *
 * @property subject Role key (`admin`/`editor`/`viewer`) or `user:id`/`team:id` reference; also the edit/delete key.
 * @property label Display name shown for the subject.
 * @property canWrite Whether the row grants write access.
 * @property inherited Whether the grant comes from an ancestor folder, in which case it is shown read-only.
 */
data class PermissionRow(
  val subject: String,
  val label: String,
  val canWrite: Boolean,
  val inherited: Boolean,
)

/**
 * Builds the rows shown in the dialog: the three fixed role rows, then the folder's own user and
 * team grants, then the grants inherited from ancestor folders that the folder does not itself
 * override. Administrators always write and the viewer baseline is always read-only; editors write
 * unless a `ForRole(WRITE)` grant in the chain holds them to view.
 *
 * @param folderId The folder whose permission rows are built.
 * @param folderPermissionRepository Repository used to read the folder's grant chain.
 * @param userRepository Repository used to resolve user display names.
 * @param teamRepository Repository used to resolve team names.
 * @return The role rows, then own grant rows, then inherited grant rows, each group ordered by creation.
 */
suspend fun permissionRows(
  folderId: String,
  folderPermissionRepository: FolderPermissionRepository,
  userRepository: UserRepository,
  teamRepository: TeamRepository,
): List<PermissionRow> {
  val chain = folderPermissionRepository.findGrantsInChain(folderId)
  val (own, inherited) = chain.partition { it.folderId == folderId }

  val ownGrants = own.subjectGrants()
  val ownKeys = ownGrants.mapTo(mutableSetOf()) { subjectKey(it.subject) }
  val inheritedGrants = inherited.subjectGrants().filterNot { subjectKey(it.subject) in ownKeys }

  val labels = resolveLabels(ownGrants + inheritedGrants, userRepository, teamRepository)

  val roles =
    listOf(
      PermissionRow("admin", "Admin", canWrite = true, inherited = false),
      PermissionRow("editor", "Editor", canWrite = chain.editorsCanWrite(), inherited = false),
      PermissionRow("viewer", "Viewer", canWrite = false, inherited = false),
    )

  return roles +
    ownGrants.map { it.toRow(inherited = false, labels) } +
    inheritedGrants.map { it.toRow(inherited = true, labels) }
}

/**
 * Builds the `type:id` reference identifying a subject in the dialog.
 *
 * @param subject The grant subject to encode.
 * @return `user:<oidcSub>`, `team:<teamId>` or the role key.
 */
internal fun subjectKey(subject: PermissionSubject): String =
  when (subject) {
    is PermissionSubject.ForUser -> "user:${subject.oidcSub}"
    is PermissionSubject.ForTeam -> "team:${subject.teamId}"
    is PermissionSubject.ForRole -> subject.role.key
  }

/** The user and team grants in this list ordered by creation; role grants are dropped. */
private fun List<FolderPermission>.subjectGrants(): List<FolderPermission> =
  filter { it.subject !is PermissionSubject.ForRole }.sortedBy { it.createdAt }

/**
 * Whether editors may write the folder this grant chain belongs to, mirroring the permission
 * checker: editors write an unrestricted folder, or a restricted one only while a `ForRole(WRITE)`
 * grant in the chain still confers write — so an editor restriction set on an ancestor is reflected.
 */
private fun List<FolderPermission>.editorsCanWrite(): Boolean =
  isEmpty() || any { (it.subject as? PermissionSubject.ForRole)?.role == Role.WRITE && it.canWrite }

private fun FolderPermission.toRow(
  inherited: Boolean,
  labels: Map<String, String>,
): PermissionRow {
  val key = subjectKey(subject)
  return PermissionRow(key, labels[key] ?: rawId(subject), canWrite, inherited)
}

/** The bare identifier of a subject, used as a label fallback when the user or team is gone. */
private fun rawId(subject: PermissionSubject): String =
  when (subject) {
    is PermissionSubject.ForUser -> subject.oidcSub
    is PermissionSubject.ForTeam -> subject.teamId
    is PermissionSubject.ForRole -> subject.role.name
  }

/**
 * Resolves the display names of every user and team grant in [grants] in two batched queries,
 * keyed by [subjectKey] so a row can look its label up without a per-row lookup.
 *
 * @return A map from each grant's `type:id` reference to its display name; subjects whose user or
 *   team no longer exists are absent and fall back to their raw id.
 */
private suspend fun resolveLabels(
  grants: List<FolderPermission>,
  userRepository: UserRepository,
  teamRepository: TeamRepository,
): Map<String, String> {
  val userIds = grants.mapNotNull { (it.subject as? PermissionSubject.ForUser)?.oidcSub }.distinct()
  val teamIds = grants.mapNotNull { (it.subject as? PermissionSubject.ForTeam)?.teamId }.distinct()

  return buildMap {
    if (userIds.isNotEmpty()) {
      userRepository
        .getAll(limit = userIds.size, filter = { UserTable.oidcSub inList userIds })
        .toList()
        .forEach { put("user:${it.oidcSub}", it.displayName) }
    }
    if (teamIds.isNotEmpty()) {
      teamRepository
        .getAll(limit = teamIds.size, filter = { TeamTable.id inList teamIds })
        .toList()
        .forEach { put("team:${it.id}", it.name) }
    }
  }
}
