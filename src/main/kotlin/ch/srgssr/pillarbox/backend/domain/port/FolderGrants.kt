package ch.srgssr.pillarbox.backend.domain.port

import ch.srgssr.pillarbox.backend.domain.model.FolderPermission
import ch.srgssr.pillarbox.backend.domain.model.PermissionSubject

/**
 * Reads and writes the explicit access grants attached to folders.
 */
interface FolderGrants {
  /**
   * Finds a grant by its identifier.
   *
   * @param id The unique identifier of the grant.
   * @return The grant, or `null` if it does not exist.
   */
  suspend fun find(id: String): FolderPermission?

  /**
   * Persists a grant, updating the access level in place when the subject
   * already holds a grant on the folder.
   *
   * @param item The grant to save.
   * @return The persisted grant.
   */
  suspend fun save(item: FolderPermission): FolderPermission

  /**
   * Deletes a grant.
   *
   * @param id The unique identifier of the grant to delete.
   * @return `true` if the grant existed and was deleted, `false` otherwise.
   */
  suspend fun delete(id: String): Boolean

  /**
   * Finds the folder's own grant for a subject, ignoring inherited grants.
   *
   * @param folderId The folder whose own grant is searched.
   * @param subject The subject the grant must apply to.
   * @return The matching grant, or `null` when the folder has none for the subject.
   */
  suspend fun findGrant(
    folderId: String,
    subject: PermissionSubject,
  ): FolderPermission?

  /**
   * Retrieves the grants effective on a folder: its own and those of its ancestors.
   *
   * @param folderId The folder whose effective grants are retrieved.
   * @return The effective grants; an empty list means the folder is unrestricted.
   */
  suspend fun findGrantsInChain(folderId: String): List<FolderPermission>

  /**
   * Retrieves the grants effective on each of the given folders in a single lookup.
   *
   * @param folderIds The folders whose effective grants are retrieved.
   * @return A map from folder id to its effective grants; unrestricted folders are absent.
   */
  suspend fun findGrantsInChains(folderIds: Collection<String>): Map<String, List<FolderPermission>>
}
