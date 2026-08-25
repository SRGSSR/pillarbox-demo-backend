package ch.srgssr.pillarbox.backend.adapter.web.api.dto

import ch.srgssr.pillarbox.backend.domain.model.Folder
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * API response representation of a folder for the V1 endpoint.
 *
 * @property id Unique identifier of the folder.
 * @property name Display name of the folder.
 * @property parentId Identifier of the parent folder, or `null` for root-level folders.
 * @property createdAt Timestamp when the folder was created.
 * @property updatedAt Timestamp of the last folder update.
 * @property mediaCount Number of active media in the folder and all of its subfolders.
 */
@Serializable
data class FolderResponseV1(
  val id: String,
  val name: String,
  val parentId: String? = null,
  val createdAt: Instant,
  val updatedAt: Instant,
  val mediaCount: Long,
)

/**
 * Converts a [Folder] domain model to its V1 API response representation.
 *
 * @param mediaCount Number of active media in the folder and all of its subfolders.
 * @return A [FolderResponseV1] containing the domain model's data.
 */
fun Folder.toFolderResponseV1(mediaCount: Long) =
  FolderResponseV1(
    id = this.id,
    name = this.name,
    parentId = this.parentId,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    mediaCount = mediaCount,
  )
