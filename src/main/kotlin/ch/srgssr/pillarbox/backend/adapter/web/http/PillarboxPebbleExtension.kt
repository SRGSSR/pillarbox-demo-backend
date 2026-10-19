package ch.srgssr.pillarbox.backend.adapter.web.http

import ch.srgssr.pillarbox.backend.time.toZonedDateTime
import io.ktor.http.encodeURLParameter
import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Filter
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

/**
 * Pebble extension registering the console's custom template filters.
 *
 * @param zone The time zone dates are rendered in, exposed to templates as `timeZone`.
 */
class PillarboxPebbleExtension(
  private val zone: ZoneId,
) : AbstractExtension() {
  override fun getFilters(): Map<String, Filter> =
    mapOf(
      "datetime" to DateTimeFilter(zone),
      "datetimeInput" to DateTimeInputFilter(zone),
      "urlPart" to UrlPartFilter,
    )

  override fun getGlobalVariables(): Map<String, Any> = mapOf("timeZone" to zone.id)
}

/**
 * Formats a Kotlin [Instant] as a human-readable date and time, e.g. `6 Jun 2026, 14:32`.
 * Any other input is returned unchanged.
 *
 * @param zone The time zone the instant is read in.
 */
private class DateTimeFilter(
  private val zone: ZoneId,
) : Filter {
  private val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

  override fun getArgumentNames(): List<String>? = null

  override fun apply(
    input: Any?,
    args: Map<String, Any>?,
    self: PebbleTemplate?,
    context: EvaluationContext?,
    lineNumber: Int,
  ): Any? = (input as? Instant)?.toZonedDateTime(zone)?.format(formatter) ?: input
}

/**
 * Formats a Kotlin [Instant] as the value of an `input[type=datetime-local]`, e.g.
 * `2026-06-06T14:32`. Seconds are dropped, as the control does not accept them by default.
 * Any other input is returned unchanged.
 *
 * @param zone The time zone the instant is read in.
 */
private class DateTimeInputFilter(
  private val zone: ZoneId,
) : Filter {
  private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH)

  override fun getArgumentNames(): List<String>? = null

  override fun apply(
    input: Any?,
    args: Map<String, Any>?,
    self: PebbleTemplate?,
    context: EvaluationContext?,
    lineNumber: Int,
  ): Any? = (input as? Instant)?.toZonedDateTime(zone)?.format(formatter) ?: input
}

/**
 * Percent-encodes a string for use as one URL path segment or query value, e.g. `a#b c` becomes
 * `a%23b%20c`. Unlike Pebble's `urlencode`, spaces become `%20`, which is valid in a path.
 * A `null` input is returned unchanged.
 */
private object UrlPartFilter : Filter {
  override fun getArgumentNames(): List<String>? = null

  override fun apply(
    input: Any?,
    args: Map<String, Any>?,
    self: PebbleTemplate?,
    context: EvaluationContext?,
    lineNumber: Int,
  ): Any? = input?.toString()?.encodeURLParameter()
}
