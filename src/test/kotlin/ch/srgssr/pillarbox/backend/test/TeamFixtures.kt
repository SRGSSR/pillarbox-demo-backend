package ch.srgssr.pillarbox.backend.test

import ch.srgssr.pillarbox.backend.adapter.persistence.team.TeamRepository
import ch.srgssr.pillarbox.backend.domain.model.Team
import io.ktor.server.testing.ApplicationTestBuilder

fun teamFixture(
  id: String = "test-team",
  name: String = "Test Team",
) = Team(
  id = id,
  name = name,
)

suspend fun ApplicationTestBuilder.seedTeam(team: Team = teamFixture()): Team {
  startApplication()
  return TeamRepository(testDb).save(team)
}
