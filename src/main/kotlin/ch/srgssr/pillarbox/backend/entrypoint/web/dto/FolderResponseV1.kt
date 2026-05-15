package ch.srgssr.pillarbox.backend.entrypoint.web.dto

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
 */
@Serializable
data class FolderResponseV1(
  val id: String,
  val name: String,
  val parentId: String? = null,
  val createdAt: Instant,
  val updatedAt: Instant,
)

/**
 * Converts a [Folder] domain model to its V1 API response representation.
 *
 * @return A [FolderResponseV1] containing the domain model's data.
 */
fun Folder.toFolderResponseV1() =
  FolderResponseV1(
    id = this.id,
    name = this.name,
    parentId = this.parentId,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
  )
