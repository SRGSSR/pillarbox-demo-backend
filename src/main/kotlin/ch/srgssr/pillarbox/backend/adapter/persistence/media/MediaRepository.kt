package ch.srgssr.pillarbox.backend.adapter.persistence.media

import ch.srgssr.pillarbox.backend.adapter.persistence.FullTextSearch
import ch.srgssr.pillarbox.backend.adapter.persistence.SearchableRepository
import ch.srgssr.pillarbox.backend.domain.catalog.MediaCriteria
import ch.srgssr.pillarbox.backend.domain.catalog.MediaVisibility
import ch.srgssr.pillarbox.backend.domain.catalog.PaginatedResult
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.port.MediaCatalog
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
 * translates [MediaCriteria] into SQL.
 *
 * @param db The [Database] instance used for all transactions.
 * @param clock The clock visibility predicates and timestamps are evaluated with.
 */
class MediaRepository(
  db: Database,
  private val clock: Clock = Clock.System,
) : SearchableRepository<Media, String>(
    db = db,
    table = MediaTable,
    idColumn = MediaTable.id,
    textSearch = FullTextSearch(MediaTable, column = "search_vector"),
  ),
  MediaCatalog {
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
      expiresAt = this[MediaTable.expiresAt]?.toKotlinInstant(),
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
    builder[MediaTable.createdAt] = clock.now().toUtcOffsetDateTime()
    builder[MediaTable.lastModified] = clock.now().toUtcOffsetDateTime()
    builder[MediaTable.expiresAt] = item.expiresAt?.toUtcOffsetDateTime()
  }

  /**
   * Encodes a [Media] domain object into an [UpdateBuilder] for updates.
   */
  override fun encodeOnUpdate(item: Media): (UpsertBuilder.(UpdateStatement) -> Unit) =
    {
      it[MediaTable.tags] = item.tags
      it[MediaTable.sources] = item.sources
      it[MediaTable.metadata] = item.metadata
      it[MediaTable.lastModified] = clock.now().toUtcOffsetDateTime()
      it[MediaTable.expiresAt] = item.expiresAt?.toUtcOffsetDateTime()
    }

  /**
   * Finds a media item by its identifier, narrowed to the given visibility.
   *
   * @param id The unique identifier of the media.
   * @param visibility What the caller is allowed to see.
   * @return The media if it exists within [visibility], or `null` otherwise.
   */
  override suspend fun find(
    id: String,
    visibility: MediaVisibility,
  ): Media? = findOne { (MediaTable.id eq id) and visibility.toPredicate(clock.now()) }

  /**
   * Retrieves the page of media matching [criteria].
   *
   * A criteria with search text returns the page most relevant first; without text the
   * page is returned in storage order.
   *
   * @param criteria The catalogue query in domain terms.
   * @param slice The window of the result to return.
   * @return A [PaginatedResult] with the matching media and the total number of matches.
   */
  override suspend fun page(
    criteria: MediaCriteria,
    slice: QuerySlice,
  ): PaginatedResult<Media> {
    val filter = { criteria.visibility.toPredicate(clock.now()) and criteria.scope.toPredicate() }
    return when {
      criteria.text.isNullOrBlank() -> getAllPaginated(slice.limit, slice.offset, filter)
      else -> search(criteria.text, slice.limit, slice.offset, filter)
    }
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
  override suspend fun updateTags(
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
        it[lastModified] = clock.now().toUtcOffsetDateTime()
      }

      updatedTags
    }

  /**
   * Marks the media item with the given [id] as deleted without removing it from the database.
   *
   * @param id The unique identifier of the media to soft-delete.
   * @return `true` if the media was found and marked as deleted, `false` if it was already deleted or does not exist.
   */
  override suspend fun softDelete(id: String): Boolean =
    query {
      MediaTable.update({ (MediaTable.id eq id) and (MediaTable.deleted eq false) }) {
        it[deleted] = true
        it[lastModified] = clock.now().toUtcOffsetDateTime()
      } > 0
    }

  /**
   * Restores a previously soft-deleted media item, making it active again.
   *
   * @param id The unique identifier of the media to restore.
   * @return `true` if the media was found and restored, `false` if it was not deleted or does not exist.
   */
  override suspend fun restore(id: String): Boolean =
    query {
      MediaTable.update({ (MediaTable.id eq id) and (MediaTable.deleted eq true) }) {
        it[deleted] = false
        it[lastModified] = clock.now().toUtcOffsetDateTime()
      } > 0
    }
}
