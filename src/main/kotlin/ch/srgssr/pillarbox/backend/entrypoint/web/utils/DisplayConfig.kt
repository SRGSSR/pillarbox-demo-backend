package ch.srgssr.pillarbox.backend.entrypoint.web.utils

import io.ktor.server.config.ApplicationConfig
import java.time.ZoneId

/**
 * Configuration parameters for how the console renders values.
 *
 * @property timeZone The time zone dates are rendered and read in. Defaults to `Europe/Zurich`.
 */
data class DisplayConfig(
  val timeZone: ZoneId = ZoneId.of("Europe/Zurich"),
)

/**
 * Extracts [DisplayConfig] from the application's configuration file.
 *
 * @return A populated [DisplayConfig] instance.
 */
fun ApplicationConfig.toDisplayConfig(): DisplayConfig {
  val display = config("display")

  return DisplayConfig(
    timeZone =
      display
        .propertyOrNull("timeZone")
        ?.getString()
        ?.let(ZoneId::of)
        ?: ZoneId.of("Europe/Zurich"),
  )
}
