package ch.srgssr.pillarbox.backend.entrypoint.web.utils

import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Filter
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

/**
 * Pebble extension registering the console's custom template filters.
 */
class PillarboxPebbleExtension : AbstractExtension() {
  override fun getFilters(): Map<String, Filter> = mapOf("datetime" to DateTimeFilter())
}

/**
 * Formats a Kotlin [Instant] as a human-readable UTC date and time, e.g. `6 Jun 2026, 14:32`.
 * Any other input is returned unchanged.
 */
private class DateTimeFilter : Filter {
  private val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

  override fun getArgumentNames(): List<String>? = null

  override fun apply(
    input: Any?,
    args: Map<String, Any>?,
    self: PebbleTemplate?,
    context: EvaluationContext?,
    lineNumber: Int,
  ): Any? = (input as? Instant)?.toUtcOffsetDateTime()?.format(formatter) ?: input
}
