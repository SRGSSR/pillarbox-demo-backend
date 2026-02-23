package ch.srgssr.pillarbox.backend.entrypoint.web

import ch.srgssr.pillarbox.backend.module
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class MediaRouteTest :
  ShouldSpec({
    should("Return internal server error on unimplemented REST call") {
      testApplication {
        application { module() }

        val response = client.get("/v1/media")

        response shouldHaveStatus HttpStatusCode.InternalServerError
      }
    }
  })
