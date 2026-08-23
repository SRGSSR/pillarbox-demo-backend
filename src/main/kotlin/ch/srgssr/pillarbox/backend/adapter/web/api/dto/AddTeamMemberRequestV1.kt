package ch.srgssr.pillarbox.backend.adapter.web.api.dto

import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (V1) representing a request to add a user to a team.
 *
 * @property oidcSub The OIDC sub of the user to add to the team.
 */
@Serializable
data class AddTeamMemberRequestV1(
  val oidcSub: String,
)
