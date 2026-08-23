package ch.srgssr.pillarbox.backend.adapter.web.api.dto

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
