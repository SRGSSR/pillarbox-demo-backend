package ch.srgssr.pillarbox.backend.adapter.web.console

import ch.srgssr.pillarbox.backend.adapter.web.http.user
import io.ktor.http.ContentType
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.pebble.respondTemplate
import java.util.Locale

/**
 * Lightweight view model of the authenticated user, intended for Pebble template rendering.
 *
 * @property name Display name of the user.
 * @property initials Two-letter initials derived from the user's name.
 * @property roles Names of the implicit roles held by the user.
 */
data class UserView(
  val name: String,
  val initials: String,
  val roles: List<String>,
)

/**
 * Builds a template model map containing the current authenticated user as a [UserView].
 *
 * @return A map with a single `"user"` entry holding a [UserView] for the authenticated user.
 */
fun ApplicationCall.userContext(): Map<String, Any> =
  mapOf(
    "user" to
      UserView(
        name = user.displayName,
        initials = user.initials,
        roles =
          user.roles
            .flatMap { it.effectiveRoles }
            .map { it.name }
            .distinct(),
      ),
  )

/**
 * Responds with a Pebble [template], automatically merging [userContext] into [model].
 *
 * @param template Classpath path to the Pebble template file.
 * @param model Additional template variables merged on top of the user context.
 * @param locale Locale passed to the Pebble engine; defaults to the JVM default when `null`.
 * @param etag Optional `ETag` value set on the response.
 * @param contentType Response `Content-Type`; defaults to `text/html; charset=UTF-8`.
 */
suspend fun ApplicationCall.respondWithContext(
  template: String,
  model: Map<String, Any> = emptyMap(),
  locale: Locale? = null,
  etag: String? = null,
  contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8),
) = respondTemplate(template, userContext() + model, locale, etag, contentType)
