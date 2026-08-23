package ch.srgssr.pillarbox.backend.adapter.web.api.dto

import ch.srgssr.pillarbox.backend.domain.model.Folder
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (V1) representing a folder request from the admin web entry point.
 *
 * This class acts as the external contract for the Pillarbox API. It decouples the
 * public API schema from the internal domain logic, allowing for versioned
 * evolution of the folder structure.
 *
 * @property name The name of the folder.
 * @property parentId This folder parent or null if it belongs to the root directory.
 */
@Serializable
data class FolderRequestV1(
  val name: String,
  val parentId: String? = null,
) {
  /**
   * Maps the [FolderRequestV1] DTO to the internal [Folder] domain model.
   *
   * Use this method to pass the validated request data into the service or
   * repository layers where domain-specific logic is applied.
   *
   * @return A [Folder] instance populated with the request's data.
   */
  fun toFolder() =
    Folder(
      name = name,
      parentId = parentId,
    )
}
