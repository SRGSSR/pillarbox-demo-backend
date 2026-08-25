package ch.srgssr.pillarbox.backend.adapter.web.api.dto

import kotlinx.serialization.Serializable

/**
 * Represents a request to import a media from the SRG SSR Integration Layer by URN.
 *
 * @property urn The SRG SSR URN identifying the media to import.
 */
@Serializable
data class ImportMediaRequestV1(
  val urn: String,
)
