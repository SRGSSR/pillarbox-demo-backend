package ch.srgssr.pillarbox.backend.persistence.media

import ch.srgssr.pillarbox.backend.db.ExposedRepository
import ch.srgssr.pillarbox.backend.domain.model.Media
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

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
    )

  /**
   * Encodes a [Media] domain object into an [UpdateBuilder] for inserts or upserts.
   */
  override fun Table.encode(
    builder: UpdateBuilder<*>,
    item: Media,
  ) {
    builder[MediaTable.id] = item.id
    builder[MediaTable.tags] = item.tags
    builder[MediaTable.sources] = item.sources
    builder[MediaTable.metadata] = item.metadata
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

      MediaTable.update({ MediaTable.id eq id }) { it[tags] = updatedTags }

      updatedTags
    }
}
