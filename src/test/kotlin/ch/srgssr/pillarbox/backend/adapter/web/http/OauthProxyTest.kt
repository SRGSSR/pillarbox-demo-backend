package ch.srgssr.pillarbox.backend.adapter.web.http

import ch.srgssr.pillarbox.backend.adapter.web.api.Navigation
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url

class OauthProxyTest :
  ShouldSpec({

    should("build callback URL using X-Forwarded headers when toggle is ON") {
      testApplicationContext(enableProxyHeaders = true) {
        val response =
          client.get(Navigation.LOGIN) {
            header(HttpHeaders.XForwardedProto, "https")
            header(HttpHeaders.XForwardedHost, "pillarbox.ch")
            header(HttpHeaders.XForwardedPort, "443")
          }

        val location = response.headers[HttpHeaders.Location]?.let { Url(it) }

        location.shouldNotBeNull()
        location.parameters["redirect_uri"] shouldBe "https://pillarbox.ch${Navigation.CALLBACK}"
      }
    }

    should("should preserve the port if it is non-standard for the scheme") {
      testApplicationContext(enableProxyHeaders = true) {
        val response =
          client.get(Navigation.LOGIN) {
            header(HttpHeaders.XForwardedProto, "http")
            header(HttpHeaders.XForwardedHost, "pillarbox.ch")
            header(HttpHeaders.XForwardedPort, "4001")
          }

        val location = response.headers[HttpHeaders.Location]?.let { Url(it) }

        location.shouldNotBeNull()
        location.parameters["redirect_uri"] shouldBe "http://pillarbox.ch:4001${Navigation.CALLBACK}"
      }
    }

    should("ignore X-Forwarded headers when toggle is OFF") {
      testApplicationContext(enableProxyHeaders = false) {
        val response =
          client.get(Navigation.LOGIN) {
            header(HttpHeaders.XForwardedProto, "https")
            header(HttpHeaders.XForwardedHost, "pillarbox.ch")
          }

        val location = response.headers[HttpHeaders.Location]?.let { Url(it) }

        location.shouldNotBeNull()
        location.parameters["redirect_uri"] shouldBe "http://localhost${Navigation.CALLBACK}"
      }
    }

    should("ignore Forwarded headers (RFC 7239) even when the toggle is ON") {
      testApplicationContext(enableProxyHeaders = true) {
        val response =
          client.get(Navigation.LOGIN) {
            header(HttpHeaders.Forwarded, "host=proxy.com;proto=https")
          }

        val location = response.headers[HttpHeaders.Location]?.let { Url(it) }

        location.shouldNotBeNull()
        location.parameters["redirect_uri"] shouldBe "http://localhost${Navigation.CALLBACK}"
      }
    }
  })
