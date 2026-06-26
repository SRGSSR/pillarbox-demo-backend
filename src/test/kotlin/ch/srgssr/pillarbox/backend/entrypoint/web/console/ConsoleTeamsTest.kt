package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.api.Navigation
import ch.srgssr.pillarbox.backend.persistence.team.TeamRepository
import ch.srgssr.pillarbox.backend.test.count
import ch.srgssr.pillarbox.backend.test.hxGet
import ch.srgssr.pillarbox.backend.test.hxPost
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
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
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

    should("show the new-team button only to administrators") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        Jsoup.parse(client.get("${Navigation.CONSOLE}/teams").bodyAsText()).count("[data-open-team]") shouldBe 1
      }
    }

    should("hide the new-team button from non-administrators") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))
        Jsoup.parse(client.get("${Navigation.CONSOLE}/teams").bodyAsText()).count("[data-open-team]") shouldBe 0
      }
    }

    should("offer matching users as member options and render a member row") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedUser(userFixture(oidcSub = "grace", displayName = "Grace Hopper"))

        val options = Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/member-options?q=grace").bodyAsText())
        options.select("option").attr("value") shouldBe "Grace Hopper"
        options.select("option").attr("data-id") shouldBe "grace"

        val row = Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/member-row?oidcSub=grace").bodyAsText())
        row.count(".member-row[data-member-id=grace]") shouldBe 1
        row.select("input[name=memberId]").attr("value") shouldBe "grace"
      }
    }

    should("create a team with its members in a single request") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedUser(userFixture(oidcSub = "m1", displayName = "Mia One"))
        seedUser(userFixture(oidcSub = "m2", displayName = "Max Two"))

        val added =
          client.hxPost("${Navigation.CONSOLE}/actions/team") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("name" to "Newsroom", "memberId" to "m1", "memberId" to "m2").formUrlEncode())
          }
        added shouldHaveStatus HttpStatusCode.OK
        parseRows(added.bodyAsText()).select(".team-row").memberCountOf("Newsroom") shouldBe "2"

        val table = parseRows(client.hxGet("${Navigation.CONSOLE}/fragments/team-table").bodyAsText())
        table.select(".cell-name").eachText() shouldContain "Newsroom"
      }
    }

    should("reject a team without a name") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))

        client.hxPost("${Navigation.CONSOLE}/actions/team") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody(listOf("name" to "  ").formUrlEncode())
        } shouldHaveStatus HttpStatusCode.UnprocessableEntity
      }
    }

    should("forbid non-administrators from creating teams") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))

        client.hxGet("${Navigation.CONSOLE}/fragments/team-form") shouldHaveStatus HttpStatusCode.Forbidden
        client.hxPost("${Navigation.CONSOLE}/actions/team") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody(listOf("name" to "Nope").formUrlEncode())
        } shouldHaveStatus HttpStatusCode.Forbidden
      }
    }

    should("show an edit action on team rows only to administrators") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedTeam(teamFixture(id = "t1", name = "Newsroom"))
        val rows = parseRows(client.hxGet("${Navigation.CONSOLE}/fragments/team-table").bodyAsText())
        rows.count(".team-row [hx-get*=team-form]") shouldBe 1
      }
    }

    should("hide the edit action from non-administrators") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))
        seedTeam(teamFixture(id = "t1", name = "Newsroom"))
        val rows = parseRows(client.hxGet("${Navigation.CONSOLE}/fragments/team-table").bodyAsText())
        rows.count(".team-row [hx-get*=team-form]") shouldBe 0
      }
    }

    should("populate the edit form with the team and its members") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedUser(userFixture(oidcSub = "m1", displayName = "Mia One"))
        seedTeam(teamFixture(id = "t1", name = "Newsroom"))
        with(TeamRepository(testDb)) { addMember("t1", "m1") }

        val doc = Jsoup.parse(client.hxGet("${Navigation.CONSOLE}/fragments/team-form?teamId=t1").bodyAsText())

        doc.select("input[name=name]").attr("value") shouldBe "Newsroom"
        doc.count(".member-row[data-member-id=m1]") shouldBe 1
        doc.select("form").attr("hx-post") shouldContain "/actions/team/t1"
      }
    }

    should("update a team's name and members in a single request") {
      testApplicationContext {
        login(roles = setOf(Role.ADMIN))
        seedUser(userFixture(oidcSub = "m1", displayName = "Mia One"))
        seedUser(userFixture(oidcSub = "m2", displayName = "Max Two"))
        seedTeam(teamFixture(id = "t1", name = "Newsroom"))
        with(TeamRepository(testDb)) { addMember("t1", "m1") }

        val updated =
          client.hxPost("${Navigation.CONSOLE}/actions/team/t1") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("name" to "Sports Desk", "memberId" to "m2").formUrlEncode())
          }
        updated shouldHaveStatus HttpStatusCode.OK
        parseRows(updated.bodyAsText()).select(".team-row").memberCountOf("Sports Desk") shouldBe "1"

        val table =
          parseRows(
            client.hxGet("${Navigation.CONSOLE}/fragments/team-table").bodyAsText(),
          ).select(".cell-name").eachText()
        table shouldContain "Sports Desk"
        table shouldNotContain "Newsroom"
      }
    }

    should("forbid non-administrators from updating a team") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))
        seedTeam(teamFixture(id = "t1", name = "Newsroom"))

        client.hxPost("${Navigation.CONSOLE}/actions/team/t1") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody(listOf("name" to "Nope").formUrlEncode())
        } shouldHaveStatus HttpStatusCode.Forbidden
      }
    }
  })

/** The text of the member-count cell (second column) of the row whose name matches [name]. */
private fun Elements.memberCountOf(name: String): String =
  first { it.selectFirst(".cell-name")?.text() == name }.select("td")[1].text()
