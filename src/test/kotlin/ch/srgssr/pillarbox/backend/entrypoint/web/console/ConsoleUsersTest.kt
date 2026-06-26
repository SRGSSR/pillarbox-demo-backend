package ch.srgssr.pillarbox.backend.entrypoint.web.console

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.entrypoint.web.api.Navigation
import ch.srgssr.pillarbox.backend.test.count
import ch.srgssr.pillarbox.backend.test.hxGet
import ch.srgssr.pillarbox.backend.test.login
import ch.srgssr.pillarbox.backend.test.seedUser
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.userFixture
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class ConsoleUsersTest :
  ShouldSpec({
    should("render the users page shell that lazy-loads the table") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))

        val doc = Jsoup.parse(client.get("${Navigation.CONSOLE}/users").bodyAsText())

        doc.count("input[type=search][name=q]") shouldBe 1
        doc.count("#user-rows[hx-get*=user-table]") shouldBe 1
      }
    }

    should("list users ordered by most recently updated, without exposing the oidc subject") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))
        seedUser(userFixture(oidcSub = "secret-sub-aaa", displayName = "Aaron Early"))
        seedUser(userFixture(oidcSub = "secret-sub-bbb", displayName = "Bianca Late", roles = setOf(Role.WRITE)))

        val body = client.hxGet("${Navigation.CONSOLE}/fragments/user-table?page=0").bodyAsText()
        val names = parseRows(body).select(".user-name").eachText()

        (names.indexOf("Bianca Late") < names.indexOf("Aaron Early")).shouldBeTrue()
        body shouldNotContain "secret-sub"
        body shouldContain "WRITE"
      }
    }

    should("filter the table by display name") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))
        seedUser(userFixture(oidcSub = "u-grace", displayName = "Grace Hopper"))
        seedUser(userFixture(oidcSub = "u-alan", displayName = "Alan Turing"))

        val names =
          parseRows(client.hxGet("${Navigation.CONSOLE}/fragments/user-table?q=grace").bodyAsText())
            .select(".user-name")
            .eachText()

        names shouldContain "Grace Hopper"
        names shouldNotContain "Alan Turing"
      }
    }

    should("page through the table with a cursor that carries the search query") {
      testApplicationContext {
        login(roles = setOf(Role.WRITE))
        repeat(3) { seedUser(userFixture(oidcSub = "match-$it", displayName = "Match $it")) }

        val doc = parseRows(client.hxGet("${Navigation.CONSOLE}/fragments/user-table?q=match&pageSize=2").bodyAsText())

        doc.count(".user-row") shouldBe 2
        val nextUrl = doc.select(".user-row[hx-get]").attr("hx-get")
        nextUrl shouldContain "page=1"
        nextUrl shouldContain "q=match"
      }
    }

    should("forbid users without write access from the page and the fragment") {
      testApplicationContext {
        login(roles = emptySet())

        client.get("${Navigation.CONSOLE}/users") shouldHaveStatus HttpStatusCode.Forbidden
        client.hxGet("${Navigation.CONSOLE}/fragments/user-table") shouldHaveStatus HttpStatusCode.Forbidden
      }
    }
  })

/** Wraps a bare `<tr>` fragment in a table so Jsoup's HTML parser keeps the rows. */
private fun parseRows(fragment: String): Document = Jsoup.parse("<table>$fragment</table>")
