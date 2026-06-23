package ch.srgssr.pillarbox.backend.persistence.folder

import ch.srgssr.pillarbox.backend.db.ExposedRepository
import ch.srgssr.pillarbox.backend.domain.model.Folder
import ch.srgssr.pillarbox.backend.persistence.media.MediaTable
import ch.srgssr.pillarbox.backend.time.toKotlinInstant
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpsertBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock

/**
 * Repository responsible for the persistence and retrieval of [Folder] entities using Exposed.
 *
 * This implementation maps the [Folder] domain model to the [FolderTable] schema.
 *
 * @param db The [Database] instance used for all transactions.
 */
@SuppressWarnings("TooManyFunctions")
class FolderRepository(
  db: Database,
) : ExposedRepository<Folder, String>(db = db, table = FolderTable, idColumn = FolderTable.id) {
  /**
   * Decodes a [ResultRow] from the [FolderTable] into a [Folder] domain object.
   */
  override fun ResultRow.decode() =
    Folder(
      id = this[FolderTable.id],
      name = this[FolderTable.name],
      parentId = this[FolderTable.parentId],
      createdAt = this[FolderTable.createdAt].toKotlinInstant(),
      updatedAt = this[FolderTable.updatedAt].toKotlinInstant(),
    )

  /**
   * Encodes a [Folder] domain object into an [UpdateBuilder] for inserts.
   */
  override fun Table.encode(
    builder: UpdateBuilder<*>,
    item: Folder,
  ) {
    builder[FolderTable.id] = item.id
    builder[FolderTable.name] = item.name
    builder[FolderTable.parentId] = item.parentId
    builder[FolderTable.createdAt] = Clock.System.now().toUtcOffsetDateTime()
    builder[FolderTable.updatedAt] = Clock.System.now().toUtcOffsetDateTime()
  }

  /**
   * Encodes a [Folder] domain object into an [UpdateBuilder] for updates.
   */
  override fun encodeOnUpdate(item: Folder): (UpsertBuilder.(UpdateStatement) -> Unit) =
    {
      it[FolderTable.name] = item.name
      it[FolderTable.parentId] = item.parentId
      it[FolderTable.updatedAt] = Clock.System.now().toUtcOffsetDateTime()
    }

  /**
   * Retrieves all ancestor folders of the given folder, ordered from the root down to the
   * folder in parameter.
   *
   * @param folderId The ID of the folder whose ancestors should be retrieved.
   * @return A list of [Folder] entities from the root down to the folder itself.
   */
  suspend fun findAncestors(folderId: String): List<Folder> =
    query(readOnly = true) {
      FolderAncestorView
        .selectAll()
        .where { FolderAncestorView.descendantId eq folderId }
        .orderBy(FolderAncestorView.depth, SortOrder.DESC)
        .map { row ->
          Folder(
            id = row[FolderAncestorView.id],
            name = row[FolderAncestorView.name],
            parentId = row[FolderAncestorView.parentId],
            createdAt = row[FolderAncestorView.createdAt].toKotlinInstant(),
            updatedAt = row[FolderAncestorView.updatedAt].toKotlinInstant(),
          )
        }
    }

  /**
   * Finds the folder a media item is assigned to.
   *
   * @param mediaId The media ID to look up.
   * @return The [Folder], or `null` if the media is not assigned to any folder.
   */
  suspend fun findFolderOf(mediaId: String): Folder? =
    query(readOnly = true) {
      (FolderTable innerJoin FolderMediaTable)
        .selectAll()
        .where { FolderMediaTable.mediaId eq mediaId }
        .singleOrNull()
        ?.decode()
    }

  /**
   * Returns `true` if the given media item is already assigned to the given folder.
   *
   * @param folderId The folder ID to check.
   * @param mediaId The media ID to check.
   */
  suspend fun isMediaInFolder(
    folderId: String,
    mediaId: String,
  ): Boolean =
    query(readOnly = true) {
      FolderMediaTable
        .selectAll()
        .where { (FolderMediaTable.folderId eq folderId) and (FolderMediaTable.mediaId eq mediaId) }
        .count() > 0
    }

  /**
   * Assigns a media item to a folder. If the media is already assigned to another folder,
   * the assignment is moved to the specified folder.
   *
   * @param folderId The ID of the target folder.
   * @param mediaId The ID of the media item to assign.
   */
  suspend fun assignMedia(
    folderId: String,
    mediaId: String,
  ): Unit =
    query {
      FolderMediaTable.upsert(FolderMediaTable.mediaId) {
        it[FolderMediaTable.mediaId] = mediaId
        it[FolderMediaTable.folderId] = folderId
        it[FolderMediaTable.addedAt] = Clock.System.now().toUtcOffsetDateTime()
      }
    }

  /**
   * Removes the folder assignment for a given media item.
   *
   * @param mediaId The ID of the media item to unassign.
   * @param folderId The folder ID currently assigned to this media.
   * @return `true` if an assignment was removed, `false` if the media had no folder assignment.
   */
  suspend fun removeMediaAssignment(
    folderId: String,
    mediaId: String,
  ): Boolean =
    query {
      FolderMediaTable.deleteWhere {
        (FolderMediaTable.mediaId eq mediaId) and (FolderMediaTable.folderId eq folderId)
      } > 0
    }

  /**
   * Counts the number of non-deleted media items in a folder, or unassigned media if [folderId] is `null`.
   *
   * @param folderId The folder ID to count media for, or `null` to count media not assigned to any folder.
   * @return The number of matching media items.
   */
  suspend fun countMediaIn(folderId: String?): Long =
    query(readOnly = true) {
      if (folderId != null) {
        (MediaTable leftJoin FolderMediaTable)
          .selectAll()
          .where { (MediaTable.deleted eq false) and (FolderMediaTable.folderId eq folderId) }
          .count()
      } else {
        (MediaTable leftJoin FolderMediaTable)
          .selectAll()
          .where { (MediaTable.deleted eq false) and FolderMediaTable.mediaId.isNull() }
          .count()
      }
    }

  /**
   * Counts the number of non-deleted media items in each of the given folders,
   * and optionally unassigned media if `null` is included.
   *
   * @param folderIds The folder IDs to count media for; include `null` to count unassigned media.
   * @return A map of folder ID (or `null`) to media count.
   */
  suspend fun countMediaIn(vararg folderIds: String?): Map<String?, Long> =
    query(readOnly = true) {
      val named = folderIds.filterNotNull().distinct()
      val results = named.associateWith { 0L }.toMutableMap<String?, Long>()
      if (named.isNotEmpty()) {
        val mediaCount = FolderMediaTable.mediaId.count()
        (MediaTable innerJoin FolderMediaTable)
          .select(FolderMediaTable.folderId, mediaCount)
          .where { (MediaTable.deleted eq false) and (FolderMediaTable.folderId inList named) }
          .groupBy(FolderMediaTable.folderId)
          .forEach { row ->
            results[row[FolderMediaTable.folderId]] = row[mediaCount]
          }
      }

      if (null in folderIds) {
        results[null] =
          (MediaTable leftJoin FolderMediaTable)
            .selectAll()
            .where { (MediaTable.deleted eq false) and FolderMediaTable.mediaId.isNull() }
            .count()
      }

      results
    }

  /**
   * Counts the number of direct subfolders of a folder, or root-level folders if [folderId] is `null`.
   *
   * @param folderId The parent folder ID, or `null` to count folders with no parent.
   * @return The number of matching subfolders.
   */
  suspend fun countSubfoldersOf(folderId: String?): Long =
    query(readOnly = true) {
      FolderTable
        .selectAll()
        .where { if (folderId == null) FolderTable.parentId.isNull() else FolderTable.parentId eq folderId }
        .count()
    }

  /**
   * Counts the number of direct subfolders for each of the given parent folder IDs,
   * and optionally root-level folders if `null` is included.
   *
   * @param folderIds The parent folder IDs to count subfolders for; include `null` to count root-level folders.
   * @return A map of parent folder ID (or `null`) to subfolder count.
   */
  suspend fun countSubfoldersOf(vararg folderIds: String?): Map<String?, Long> =
    query(readOnly = true) {
      val named = folderIds.filterNotNull().distinct()
      val results = named.associateWith { 0L }.toMutableMap<String?, Long>()

      if (named.isNotEmpty()) {
        val folderCount = FolderTable.id.count()
        FolderTable
          .select(FolderTable.parentId, folderCount)
          .where { FolderTable.parentId inList named }
          .groupBy(FolderTable.parentId)
          .forEach { row ->
            results[row[FolderTable.parentId]] = row[folderCount]
          }
      }

      if (null in folderIds) {
        results[null] =
          FolderTable
            .selectAll()
            .where { FolderTable.parentId.isNull() }
            .count()
      }

      results
    }
}
