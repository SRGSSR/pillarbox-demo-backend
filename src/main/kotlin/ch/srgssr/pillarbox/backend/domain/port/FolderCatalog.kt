package ch.srgssr.pillarbox.backend.domain.port

import ch.srgssr.pillarbox.backend.domain.catalog.FolderScope
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.model.Folder

/**
 * Reads and writes the folder tree and its media assignments.
 */
@SuppressWarnings("TooManyFunctions", "ComplexInterface")
interface FolderCatalog {
  /**
   * Finds a folder by its identifier.
   *
   * @param id The unique identifier of the folder.
   * @return The folder, or `null` if it does not exist.
   */
  suspend fun find(id: String): Folder?

  /**
   * Whether a folder exists with this identifier.
   *
   * @param id The unique identifier of the folder.
   * @return `true` if it exists, `false` otherwise.
   */
  suspend fun exists(id: String): Boolean

  /**
   * Persists or overwrites a folder.
   *
   * @param item The folder to save.
   * @return The persisted folder.
   */
  suspend fun save(item: Folder): Folder

  /**
   * Deletes a folder and, through cascade, its descendants.
   *
   * @param id The unique identifier of the folder to delete.
   * @return `true` if the folder existed and was deleted, `false` otherwise.
   */
  suspend fun delete(id: String): Boolean

  /**
   * Lists folders within the given scope.
   *
   * [FolderScope.Anywhere] lists every folder, [FolderScope.Unassigned] the root level,
   * and [FolderScope.In] the direct children of a folder.
   *
   * @param scope Where in the folder tree to look.
   * @param slice The window of the result to return.
   * @return The matching folders.
   */
  suspend fun list(
    scope: FolderScope = FolderScope.Anywhere,
    slice: QuerySlice = QuerySlice(limit = 100),
  ): List<Folder>

  /**
   * Retrieves the ancestors of a folder, from the root down to the folder itself.
   *
   * @param folderId The folder whose ancestors are retrieved.
   * @return The chain of folders ending with the folder itself.
   */
  suspend fun findAncestors(folderId: String): List<Folder>

  /**
   * Finds the folder a media item is assigned to.
   *
   * @param mediaId The media to look up.
   * @return The folder, or `null` if the media is unassigned.
   */
  suspend fun findFolderOf(mediaId: String): Folder?

  /**
   * Finds the folder each of the given media items is assigned to, in a single lookup.
   *
   * @param mediaIds The media items to look up.
   * @return A map from media id to its folder; unassigned media are absent.
   */
  suspend fun findFoldersOf(mediaIds: Collection<String>): Map<String, Folder>

  /**
   * Whether the media item is assigned to the folder.
   *
   * @param folderId The folder to check.
   * @param mediaId The media to check.
   * @return `true` if the assignment exists, `false` otherwise.
   */
  suspend fun isMediaInFolder(
    folderId: String,
    mediaId: String,
  ): Boolean

  /**
   * Assigns a media item to a folder, moving it if it was assigned elsewhere.
   *
   * @param folderId The target folder.
   * @param mediaId The media to assign.
   */
  suspend fun assignMedia(
    folderId: String,
    mediaId: String,
  )

  /**
   * Removes the assignment of a media item to a folder.
   *
   * @param folderId The folder the media is assigned to.
   * @param mediaId The media to unassign.
   * @return `true` if an assignment was removed, `false` otherwise.
   */
  suspend fun removeMediaAssignment(
    folderId: String,
    mediaId: String,
  ): Boolean

  /**
   * Counts the active media in a folder and all of its descendants, or the unassigned media for `null`.
   *
   * @param folderId The folder whose subtree to count, or `null` for unassigned media.
   * @return The number of matching media items.
   */
  suspend fun countMediaIn(folderId: String?): Long

  /**
   * Counts the active media in each of the given folders and their descendants in a single lookup.
   *
   * @param folderIds The folders whose subtrees to count; include `null` to count unassigned media.
   * @return A map from folder id (or `null`) to media count.
   */
  suspend fun countMediaIn(vararg folderIds: String?): Map<String?, Long>

  /**
   * Counts the direct subfolders of a folder, or the root-level folders for `null`.
   *
   * @param folderId The parent folder, or `null` for the root level.
   * @return The number of matching folders.
   */
  suspend fun countSubfoldersOf(folderId: String?): Long

  /**
   * Counts the direct subfolders of each of the given folders in a single lookup.
   *
   * @param folderIds The parent folders to count; include `null` for the root level.
   * @return A map from parent folder id (or `null`) to subfolder count.
   */
  suspend fun countSubfoldersOf(vararg folderIds: String?): Map<String?, Long>
}
