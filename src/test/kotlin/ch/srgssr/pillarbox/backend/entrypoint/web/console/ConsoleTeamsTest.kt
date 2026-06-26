package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.api.Navigation
import ch.srgssr.pillarbox.backend.persistence.team.TeamRepository
import ch.srgssr.pillarbox.backend.test.count
import ch.srgssr.pillarbox.backend.test.hxGet
import ch.srgssr.pillarbox.backend.test.login
import ch.srgssr.pillarbox.backend.test.parseRows
import ch.srgssr.pillarbox.backend.test.seedTeam
import ch.srgssr.pillarbox.backend.test.seedUser
import ch.srgssr.pillarbox.backend.test.teamFixture
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.testDb
import ch.srgssr.pillarbox.backend.test.userFixture
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.jsoup.Jsoup
import org.jsoup.select.Elements

class ConsoleTeamsTest :
  ShouldSpec({
    should("render the teams page shell that lazy-loads the table") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))

        val doc = Jsoup.parse(client.get("${Navigation.CONSOLE}/teams").bodyAsText())

        doc.count("input[type=search][name=q]") shouldBe 1
        doc.count("#team-rows[hx-get*=team-table]") shouldBe 1
      }
    }

    should("list teams ordered by most recently updated") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))
        seedTeam(teamFixture(id = "t-early", name = "Archive"))
        seedTeam(teamFixture(id = "t-late", name = "Newsroom"))

        val names =
          parseRows(client.hxGet("${Navigation.CONSOLE}/fragments/team-table").bodyAsText())
            .select(".cell-name")
            .eachText()

        (names.indexOf("Newsroom") < names.indexOf("Archive")).shouldBeTrue()
      }
    }

    should("filter the table by name") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))
        seedTeam(teamFixture(id = "t-news", name = "Newsroom"))
        seedTeam(teamFixture(id = "t-sport", name = "Sport"))

        val names =
          parseRows(client.hxGet("${Navigation.CONSOLE}/fragments/team-table?q=news").bodyAsText())
            .select(".cell-name")
            .eachText()

        names shouldContain "Newsroom"
        names shouldNotContain "Sport"
      }
    }

    should("show the member count for each team") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))
        seedUser(userFixture(oidcSub = "u1", displayName = "One"))
        seedUser(userFixture(oidcSub = "u2", displayName = "Two"))
        seedTeam(teamFixture(id = "t-crowded", name = "Crowded"))
        seedTeam(teamFixture(id = "t-empty", name = "Empty"))
        with(TeamRepository(testDb)) {
          addMember("t-crowded", "u1")
          addMember("t-crowded", "u2")
        }

        val rows =
          parseRows(
            client.hxGet("${Navigation.CONSOLE}/fragments/team-table").bodyAsText(),
          ).select(".team-row")

        rows.memberCountOf("Crowded") shouldBe "2"
        rows.memberCountOf("Empty") shouldBe "0"
      }
    }

    should("page through the table with a cursor that carries the search query") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))
        repeat(3) { seedTeam(teamFixture(id = "match-$it", name = "Match $it")) }

        val doc =
          parseRows(client.hxGet("${Navigation.CONSOLE}/fragments/team-table?q=match&pageSize=2").bodyAsText())

        doc.count(".team-row") shouldBe 2
        val nextUrl = doc.select(".team-row[hx-get]").attr("hx-get")
        nextUrl shouldContain "page=1"
        nextUrl shouldContain "q=match"
      }
    }

    should("forbid users without write access from the page and the fragment") {
      testApplicationContext {
        login(roles = emptySet())

        client.get("${Navigation.CONSOLE}/teams") shouldHaveStatus HttpStatusCode.Forbidden
        client.hxGet("${Navigation.CONSOLE}/fragments/team-table") shouldHaveStatus HttpStatusCode.Forbidden
      }
    }
  })

/** The text of the member-count cell (second column) of the row whose name matches [name]. */
private fun Elements.memberCountOf(name: String): String =
  first { it.selectFirst(".cell-name")?.text() == name }.select("td")[1].text()
