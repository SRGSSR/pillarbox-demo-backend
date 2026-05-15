package ch.srgssr.pillarbox.backend.persistence.folder

import ch.srgssr.pillarbox.backend.persistence.folder.FolderAncestorView.depth
import ch.srgssr.pillarbox.backend.persistence.folder.FolderAncestorView.descendantId
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Provides the breadcrumb path for any folder: one row per ancestor from
 * the folder itself ([depth] = 1) up to the root.
 */
object FolderAncestorView : Table("v_folder_ancestors") {
  /** The folder whose ancestry is being traced. */
  val descendantId = varchar("descendant_id", 255)

  /** Identifier of the ancestor folder at this level. */
  val id = varchar("id", 255)

  /** Name of the ancestor folder. */
  val name = varchar("name", 255)

  /** Parent of the ancestor folder, or `null` if it is the root. */
  val parentId = varchar("parent_id", 255).nullable()

  /** Timestamp when the ancestor folder was created. */
  val createdAt = timestampWithTimeZone("created_at")

  /** Timestamp when the ancestor folder was last updated. */
  val updatedAt = timestampWithTimeZone("updated_at")

  /** Distance from [descendantId] to this ancestor. 1 means the folder itself. */
  val depth = integer("depth")
}
