package ch.srgssr.pillarbox.backend.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Serializable
data class Session(
  val sessionId: String,
  val accessToken: String,
  val lastChecked: Instant = Clock.System.now(),
  val expiresAt: Instant = lastChecked + 24.hours,
)
