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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpsertBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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
   * @return A list of ancestor [Folder] entities, from the most distant (root) to the closest (parent).
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
   * Finds the folder that a media item is currently assigned to.
   *
   * @param mediaId The ID of the media item.
   * @return The [Folder] containing the media, or `null` if the media is not assigned to any folder.
   */
  suspend fun findFolderOfMedia(mediaId: String): Folder? =
    query(readOnly = true) {
      (FolderTable innerJoin FolderMediaTable)
        .selectAll()
        .where { FolderMediaTable.mediaId eq mediaId }
        .singleOrNull()
        ?.decode()
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
        FolderMediaTable.selectAll().where { FolderMediaTable.folderId eq folderId }.count()
      } else {
        (MediaTable leftJoin FolderMediaTable)
          .selectAll()
          .where { (MediaTable.deleted eq false) and FolderMediaTable.mediaId.isNull() }
          .count()
      }
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
}
