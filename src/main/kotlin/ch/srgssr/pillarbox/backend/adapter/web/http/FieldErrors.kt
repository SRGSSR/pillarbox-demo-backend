package ch.srgssr.pillarbox.backend.adapter.web.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

/**
 * Error body that ties messages to form fields, so the client can flag each field.
 *
 * @property errors The message for each field, keyed by the field's form name (e.g. `metadata.title`).
 */
@Serializable
data class FieldErrors(
  val errors: Map<String, String>,
)

/**
 * Responds with the given [status] and a [FieldErrors] body.
 *
 * @param status The HTTP status of the response.
 * @param errors Pairs of field form name and message.
 */
suspend fun ApplicationCall.respondFieldErrors(
  status: HttpStatusCode,
  vararg errors: Pair<String, String>,
) = respond(status, FieldErrors(errors.toMap()))
