package ch.srgssr.pillarbox.backend.adapter.persistence.folder

import ch.srgssr.pillarbox.backend.adapter.persistence.media.MediaTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Junction table linking media items to folders.
 *
 * Each row represents a single media-to-folder assignment. Deleting either the
 * referenced [MediaTable] or [FolderTable] row cascades to this table.
 */
object FolderMediaTable : Table("pb_folder_media") {
  /** Identifier of the assigned media item. */
  val mediaId = varchar("media_id", 255).references(MediaTable.id, onDelete = ReferenceOption.CASCADE)

  /** Identifier of the folder the media is assigned to. */
  val folderId = varchar("folder_id", 255).references(FolderTable.id, onDelete = ReferenceOption.CASCADE)

  /** Timestamp when the media was assigned to the folder. */
  val addedAt = timestampWithTimeZone("added_at")

  override val primaryKey = PrimaryKey(mediaId)
}
