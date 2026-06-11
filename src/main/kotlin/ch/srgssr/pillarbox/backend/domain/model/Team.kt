package ch.srgssr.pillarbox.backend.domain.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents a team of users for shared content management.
 *
 * @property id Unique identifier of the team.
 * @property name Display name of the team, unique across all teams.
 * @property createdAt Timestamp of the initial creation of this team.
 * @property updatedAt Timestamp of the last modification to this team.
 */
@OptIn(ExperimentalUuidApi::class)
data class Team(
  val id: String = Uuid.random().toString(),
  val name: String,
  val createdAt: Instant = Clock.System.now(),
  val updatedAt: Instant = Clock.System.now(),
)
