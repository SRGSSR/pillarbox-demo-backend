package ch.srgssr.pillarbox.backend.authz

import ch.srgssr.pillarbox.backend.domain.model.Folder
import ch.srgssr.pillarbox.backend.domain.model.FolderPermission
import ch.srgssr.pillarbox.backend.domain.model.PermissionSubject
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.model.User
import ch.srgssr.pillarbox.backend.persistence.folder.FolderPermissionRepository
import ch.srgssr.pillarbox.backend.persistence.folder.FolderRepository
import ch.srgssr.pillarbox.backend.persistence.team.TeamRepository

/**
 * Evaluates whether a user may modify a folder or a media item.
 *
 * Administrators may always write and users without the [Role.WRITE] role never may.
 * Editors may write everywhere by default; a folder becomes restricted once it or an
 * ancestor carries explicit [FolderPermission] grants, and from then on only granted
 * subjects may write to it. Media outside any folder is unrestricted.
 *
 * @property folderPermissionRepository Repository used to read the grants of a folder chain.
 * @property folderRepository Repository used to resolve the folder of a media item.
 * @property teamRepository Repository used to resolve the team memberships of a user.
 */
class PermissionChecker(
  private val folderPermissionRepository: FolderPermissionRepository,
  private val folderRepository: FolderRepository,
  private val teamRepository: TeamRepository,
) {
  /**
   * Whether [user] may modify the folder identified by [folderId] and its content.
   *
   * @param user The authenticated user.
   * @param folderId The target folder, or `null` for the unrestricted root scope.
   * @return `true` if the user may write, `false` otherwise.
   */
  suspend fun canWriteFolder(
    user: User,
    folderId: String?,
  ): Boolean =
    when {
      user.hasAnyRole(setOf(Role.ADMIN)) -> {
        true
      }

      !user.hasAnyRole(setOf(Role.WRITE)) -> {
        false
      }

      folderId == null -> {
        true
      }

      else -> {
        val grants = folderPermissionRepository.findGrantsInChain(folderId)
        grants.isEmpty() || isGranted(user, grants, user.teamIdsFor(grants))
      }
    }

  /**
   * Whether [user] may modify each of the given folders.
   *
   * Effective grants for every folder are resolved in a single query, so a set of
   * folders costs one grant lookup regardless of their depth.
   *
   * @param user The authenticated user.
   * @param folders The folders to evaluate.
   * @return A map from folder id to whether [user] may write it.
   */
  suspend fun canWriteFolders(
    user: User,
    folders: List<Folder>,
  ): Map<String, Boolean> =
    when {
      folders.isEmpty() -> {
        emptyMap()
      }

      user.hasAnyRole(setOf(Role.ADMIN)) -> {
        folders.associate { it.id to true }
      }

      !user.hasAnyRole(setOf(Role.WRITE)) -> {
        folders.associate { it.id to false }
      }

      else -> {
        val grantsByFolder = folderPermissionRepository.findGrantsInChains(folders.map { it.id })
        val teamIds = user.teamIdsFor(grantsByFolder.values.flatten())

        folders.associate { folder ->
          val grants = grantsByFolder[folder.id].orEmpty()
          folder.id to (grants.isEmpty() || isGranted(user, grants, teamIds))
        }
      }
    }

  /**
   * Whether [user] may modify the media identified by [mediaId], based on the
   * folder it is assigned to.
   *
   * @param user The authenticated user.
   * @param mediaId The target media item.
   * @return `true` if the user may write, `false` otherwise.
   */
  suspend fun canWriteMedia(
    user: User,
    mediaId: String,
  ): Boolean = canWriteFolder(user, folderRepository.findFolderOf(mediaId)?.id)

  /**
   * Whether [user] may modify each of the given media items, based on the folders they are
   * assigned to.
   *
   * @param user The authenticated user.
   * @param mediaIds The target media items.
   * @return A map from media id to whether [user] may write it.
   */
  suspend fun canWriteMedia(
    user: User,
    mediaIds: List<String>,
  ): Map<String, Boolean> = canWriteMedia(user, mediaIds, folderRepository.findFoldersOf(mediaIds))

  /**
   * Whether [user] may modify each of the given media items, reusing an already-resolved
   * [foldersByMedia] map so the caller and this check share a single folder lookup.
   *
   * @param user The authenticated user.
   * @param mediaIds The target media items.
   * @param foldersByMedia The folder each media item is assigned to; ids absent from the map are
   *   treated as unassigned.
   * @return A map from media id to whether [user] may write it.
   */
  suspend fun canWriteMedia(
    user: User,
    mediaIds: List<String>,
    foldersByMedia: Map<String, Folder>,
  ): Map<String, Boolean> {
    val writableByFolder = canWriteFolders(user, foldersByMedia.values.distinctBy { it.id })
    val canWriteUnassigned = canWriteFolder(user, null)

    return mediaIds.associateWith { mediaId ->
      when (val folder = foldersByMedia[mediaId]) {
        null -> canWriteUnassigned
        else -> writableByFolder[folder.id] == true
      }
    }
  }

  private fun isGranted(
    user: User,
    grants: List<FolderPermission>,
    teamIds: Set<String>,
  ): Boolean =
    grants.any { grant ->
      grant.canWrite &&
        when (val subject = grant.subject) {
          is PermissionSubject.ForUser -> subject.oidcSub == user.oidcSub
          is PermissionSubject.ForTeam -> subject.teamId in teamIds
          is PermissionSubject.ForRole -> user.hasAnyRole(setOf(subject.role))
        }
    }

  private suspend fun User.teamIdsFor(grants: List<FolderPermission>): Set<String> =
    if (grants.any { it.subject is PermissionSubject.ForTeam }) {
      teamRepository.findTeamsOf(oidcSub).map { it.id }.toSet()
    } else {
      emptySet()
    }
}
