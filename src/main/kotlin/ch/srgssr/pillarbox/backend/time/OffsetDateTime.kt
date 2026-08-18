package ch.srgssr.pillarbox.backend.time

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * Converts a Java [OffsetDateTime] to a Kotlin [Instant].
 *
 * @return The [Instant] representation of this date-time.
 */
fun OffsetDateTime.toKotlinInstant() = this.toInstant().toKotlinInstant()

/**
 * Converts a Kotlin [Instant] to a Java [OffsetDateTime] at UTC.
 *
 * @return An [OffsetDateTime] set to the UTC (+00:00) offset.
 */
fun Instant.toUtcOffsetDateTime(): OffsetDateTime = this.toJavaInstant().atOffset(ZoneOffset.UTC)

/**
 * Converts a Kotlin [Instant] to a Java [ZonedDateTime] in the given [zone].
 *
 * @param zone The time zone to read this instant in.
 * @return A [ZonedDateTime] at the same instant, in [zone].
 */
fun Instant.toZonedDateTime(zone: ZoneId): ZonedDateTime = this.toJavaInstant().atZone(zone)
