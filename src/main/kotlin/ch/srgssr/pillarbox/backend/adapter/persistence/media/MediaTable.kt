package ch.srgssr.pillarbox.backend.adapter.persistence.media

import ch.srgssr.pillarbox.backend.adapter.persistence.media.MediaTable.id
import ch.srgssr.pillarbox.backend.domain.model.MediaMetadata
import ch.srgssr.pillarbox.backend.domain.model.MediaSource
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

/**
 * Exposed table definition for persisting media content.
 *
 * This table stores information about media items and their associated sources and metadata.
 */
object MediaTable : Table("pb_media") {
  /**
   * Unique identifier for the media item.
   */
  val id = varchar("id", 255)

  /**
   * A list of labels or categories associated with the media.
   */
  val tags = array<String>("tags")

  /**
   * The time when the media was deleted.
   */
  val deleted = bool("deleted")

  /**
   * The time when the media was created.
   */
  val createdAt = timestampWithTimeZone("created_at")

  /**
   * The time when the media was last modified.
   */
  val lastModified = timestampWithTimeZone("last_modified")

  /**
   * The time when this media will expire.
   */
  val expiresAt = timestampWithTimeZone("expires_at").nullable()

  /**
   * List of available delivery sources.
   * Stored as a JSONB list of [MediaSource] objects.
   */
  val sources = jsonb<List<MediaSource>>("sources", Json.Default)

  /**
   * Descriptive information about the media.
   * Stored as a single JSONB [MediaMetadata] object.
   */
  val metadata = jsonb<MediaMetadata>("metadata", Json.Default)

  /**
   * Primary key definition using the [id] column.
   */
  override val primaryKey = PrimaryKey(id)
}
