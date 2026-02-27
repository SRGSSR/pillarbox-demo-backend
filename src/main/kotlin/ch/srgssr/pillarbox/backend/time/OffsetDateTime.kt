package ch.srgssr.pillarbox.backend.time

import java.time.OffsetDateTime
import java.time.ZoneOffset
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
