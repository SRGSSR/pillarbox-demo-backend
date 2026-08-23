package ch.srgssr.pillarbox.backend.adapter.web.api.dto

import ch.srgssr.pillarbox.backend.domain.model.Team
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * API response representation of a team for the V1 endpoint.
 *
 * @property id Unique identifier of the team.
 * @property name Display name of the team.
 * @property createdAt Timestamp when the team was created.
 * @property updatedAt Timestamp of the last team update.
 */
@Serializable
data class TeamResponseV1(
  val id: String,
  val name: String,
  val createdAt: Instant,
  val updatedAt: Instant,
)

/**
 * Converts a [Team] domain model to its V1 API response representation.
 *
 * @return A [TeamResponseV1] containing the domain model's data.
 */
fun Team.toTeamResponseV1() =
  TeamResponseV1(
    id = this.id,
    name = this.name,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
  )
