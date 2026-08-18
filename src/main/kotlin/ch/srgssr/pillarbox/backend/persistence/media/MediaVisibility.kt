package ch.srgssr.pillarbox.backend.persistence.media

import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import kotlin.time.Clock

/**
 * A convenient set of predicates to filter media by visibility.
 */
object MediaVisibility {
  /** Filters out media that is expired or deleted. */
  val PLAYABLE get() = ACTIVE and notExpired()

  /** Filters out not deleted media, expired media might appear. */
  val ACTIVE = MediaTable.deleted eq false

  /** Shows only deleted media. */
  val DELETED = MediaTable.deleted eq true
}

private fun notExpired(): Op<Boolean> =
  MediaTable.expiresAt.isNull() or (MediaTable.expiresAt greater Clock.System.now().toUtcOffsetDateTime())
