package ch.srgssr.pillarbox.backend.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class Folder(
  val id: String = Uuid.random().toString(),
  val name: String,
  val parentId: String? = null,
  val createdAt: Instant = Clock.System.now(),
  val updatedAt: Instant = Clock.System.now(),
)
