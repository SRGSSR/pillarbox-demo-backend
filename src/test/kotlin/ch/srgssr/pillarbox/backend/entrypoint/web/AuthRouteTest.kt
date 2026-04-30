package ch.srgssr.pillarbox.backend.entrypoint.web

import ch.srgssr.pillarbox.backend.test.login
import ch.srgssr.pillarbox.backend.test.mockServer
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

class AuthRouteTest :
  ShouldSpec({
    should("successfully perform OAuth callback and issue a session cookie") {
      testApplicationContext {
        val response = login()
        val cookies = client.cookies("http://localhost")

        cookies.find { it.name == "PILLARBOX_SESSION_ID" } shouldNotBe null
        response shouldHaveStatus HttpStatusCode.Found
        response.headers[HttpHeaders.Location] shouldBe Navigation.CONSOLE
      }
    }

    should("redirect to OAuth provider when callback is hit with no parameters") {
      testApplicationContext {
        val response = client.get(Navigation.CALLBACK)
        response shouldHaveStatus HttpStatusCode.Found
        response.headers[HttpHeaders.Location] shouldContain
          mockServer.authorizationEndpointUrl("pillarbox-realm").toString()
        client.cookies("http://localhost").find { it.name == "PILLARBOX_SESSION_ID" } shouldBe null
      }
    }
    should("redirect to OAuth provider when callback has a forged state and code") {
      testApplicationContext {
        val response =
          client.get(Navigation.CALLBACK) {
            url {
              parameters.append("code", "fake-code")
              parameters.append("state", "fake-state")
            }
          }
        response shouldHaveStatus HttpStatusCode.Unauthorized
        client.cookies("http://localhost").find { it.name == "PILLARBOX_SESSION_ID" } shouldBe null
      }
    }

    should("redirect to login endpoint when the console path is hit with no parameters") {
      testApplicationContext {
        val response = client.get(Navigation.CONSOLE)
        response shouldHaveStatus HttpStatusCode.Found
        response.headers[HttpHeaders.Location] shouldContain Navigation.LOGIN
        client.cookies("http://localhost").find { it.name == "PILLARBOX_SESSION_ID" } shouldBe null
      }
    }

    should("clear session and redirect to end-session endpoint on logout") {
      testApplicationContext {
        login()
        client
          .cookies("http://localhost")
          .find { it.name == "PILLARBOX_SESSION_ID" } shouldNotBe null

        val response = client.get(Navigation.LOGOUT)

        response shouldHaveStatus HttpStatusCode.Found
        response.headers[HttpHeaders.Location] shouldContain
          mockServer.endSessionEndpointUrl("pillarbox-realm").toString()
        client
          .cookies("http://localhost")
          .find { it.name == "PILLARBOX_SESSION_ID" } shouldBe null
      }
    }

    should("redirect to the login endpoint when there is no active session") {
      testApplicationContext {
        val response = client.get(Navigation.LOGOUT)

        response shouldHaveStatus HttpStatusCode.Found
        response.headers[HttpHeaders.Location] shouldContain Navigation.LOGIN
      }
    }
  })
