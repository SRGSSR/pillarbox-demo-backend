package ch.srgssr.pillarbox.backend.persistence.media

import ch.srgssr.pillarbox.backend.db.ExposedRepository
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.time.toKotlinInstant
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpsertBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock

/**
 * Repository responsible for the persistence and retrieval of [Media] entities using Exposed.
 *
 * This implementation maps the [Media] domain model to the [MediaTable] schema and
 * provides specialized methods for media-specific data manipulations.
 *
 * @param db The [Database] instance used for all transactions.
 */
class MediaRepository(
  db: Database,
) : ExposedRepository<Media, String>(db = db, table = MediaTable, idColumn = MediaTable.id) {
  /**
   * Decodes a [ResultRow] from the [MediaTable] into a [Media] domain object.
   */
  override fun ResultRow.decode() =
    Media(
      id = this[MediaTable.id],
      tags = this[MediaTable.tags],
      sources = this[MediaTable.sources],
      metadata = this[MediaTable.metadata],
      deleted = this[MediaTable.deleted],
      createdAt = this[MediaTable.createdAt].toKotlinInstant(),
      lastModified = this[MediaTable.lastModified].toKotlinInstant(),
    )

  /**
   * Encodes a [Media] domain object into an [UpdateBuilder] for inserts.
   */
  override fun Table.encode(
    builder: UpdateBuilder<*>,
    item: Media,
  ) {
    builder[MediaTable.id] = item.id
    builder[MediaTable.tags] = item.tags
    builder[MediaTable.sources] = item.sources
    builder[MediaTable.metadata] = item.metadata
    builder[MediaTable.deleted] = item.deleted
    builder[MediaTable.createdAt] = Clock.System.now().toUtcOffsetDateTime()
    builder[MediaTable.lastModified] = Clock.System.now().toUtcOffsetDateTime()
  }

  /**
   * Encodes a [Media] domain object into an [UpdateBuilder] for updates.
   */
  override fun encodeOnUpdate(item: Media): (UpsertBuilder.(UpdateStatement) -> Unit) =
    {
      it[MediaTable.tags] = item.tags
      it[MediaTable.sources] = item.sources
      it[MediaTable.metadata] = item.metadata
      it[MediaTable.lastModified] = Clock.System.now().toUtcOffsetDateTime()
    }

  /**
   * Atomically updates the tags of a specific media resource.
   *
   * This method retrieves the current tags within a transaction, applies the [transform]
   * function, and persists the updated list back to the database.
   *
   * @param id The unique identifier of the media to update.
   * @param transform A lambda that receives the current list of tags and returns the new list.
   *
   * @return The updated list of tags if the media was found and updated, or `null` if the media does not exist.
   */
  suspend fun updateTags(
    id: String,
    transform: (List<String>) -> List<String>,
  ): List<String>? =
    query {
      val currentTags =
        MediaTable
          .select(MediaTable.tags)
          .where { MediaTable.id eq id }
          .singleOrNull()
          ?.get(MediaTable.tags) ?: return@query null

      val updatedTags = transform(currentTags)

      MediaTable.update({ MediaTable.id eq id }) {
        it[tags] = updatedTags
        it[lastModified] = Clock.System.now().toUtcOffsetDateTime()
      }

      updatedTags
    }

  suspend fun softDelete(id: String): Boolean =
    query {
      MediaTable.update({ (MediaTable.id eq id) and (MediaTable.deleted eq false) }) {
        it[deleted] = true
        it[lastModified] = Clock.System.now().toUtcOffsetDateTime()
      } > 0
    }

  suspend fun restore(id: String): Boolean =
    query {
      MediaTable.update({ (MediaTable.id eq id) and (MediaTable.deleted eq true) }) {
        it[deleted] = false
        it[lastModified] = Clock.System.now().toUtcOffsetDateTime()
      } > 0
    }
}
