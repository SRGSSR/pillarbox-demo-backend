package ch.srgssr.pillarbox.backend.entrypoint.web.dto

import kotlinx.serialization.Serializable

/**
 * Request body for assigning a media item to a folder.
 *
 * @property mediaId Identifier of the media item to assign.
 */
@Serializable
data class AssignMediaRequestV1(
  val mediaId: String,
)
