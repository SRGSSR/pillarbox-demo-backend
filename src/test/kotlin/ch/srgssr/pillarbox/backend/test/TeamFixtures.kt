package ch.srgssr.pillarbox.backend.test

import ch.srgssr.pillarbox.backend.domain.model.Team
import ch.srgssr.pillarbox.backend.persistence.team.TeamRepository
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
