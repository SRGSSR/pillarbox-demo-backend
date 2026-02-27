package ch.srgssr.pillarbox.backend.persistence.session

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import kotlin.time.Clock

object SessionTable : Table("pb_session") {
  val sessionId = varchar("session_id", 255)

  val accessToken = text("access_token")

  val lastChecked = timestampWithTimeZone("last_checked")

  val expiresAt = timestampWithTimeZone("expires_at")

  override val primaryKey = PrimaryKey(sessionId)
}
