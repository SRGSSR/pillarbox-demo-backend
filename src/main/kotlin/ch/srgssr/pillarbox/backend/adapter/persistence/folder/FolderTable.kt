package ch.srgssr.pillarbox.backend.adapter.persistence.folder

import ch.srgssr.pillarbox.backend.adapter.persistence.folder.FolderTable.parentId
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Exposed table definition for persisting folder metadata.
 *
 * Folders form a tree structure via the self-referencing [parentId] column.
 * Deleting a parent folder cascades to all its children. Media in the folder
 * are not deleted, see [FolderMediaTable] relation.
 */
object FolderTable : Table("pb_folders") {
  /** Unique identifier for the folder. */
  val id = varchar("id", 255)

  /** Display name of the folder. */
  val name = varchar("name", 255)

  /** Parent folder identifier, or `null` for root-level folders. */
  val parentId = varchar("parent_id", 255).references(id, onDelete = ReferenceOption.CASCADE).nullable()

  /** Timestamp when the folder was created. */
  val createdAt = timestampWithTimeZone("created_at")

  /** Timestamp when the folder was last updated. */
  val updatedAt = timestampWithTimeZone("updated_at")

  override val primaryKey = PrimaryKey(id)

  init {
    uniqueIndex(parentId, name)
  }
}
