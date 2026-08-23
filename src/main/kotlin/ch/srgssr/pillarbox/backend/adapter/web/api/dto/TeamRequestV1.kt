package ch.srgssr.pillarbox.backend.adapter.web.api.dto

import ch.srgssr.pillarbox.backend.domain.model.Team
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (V1) representing a team request from the admin web entry point.
 *
 * @property name The name of the team.
 */
@Serializable
data class TeamRequestV1(
  val name: String,
) {
  /**
   * Maps the [TeamRequestV1] DTO to the internal [Team] domain model.
   *
   * @return A [Team] instance populated with the request's data.
   */
  fun toTeam() = Team(name = name)
}
