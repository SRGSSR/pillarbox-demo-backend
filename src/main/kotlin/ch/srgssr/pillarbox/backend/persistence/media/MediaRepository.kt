package ch.srgssr.pillarbox.backend.persistence.media

import ch.srgssr.pillarbox.backend.db.FullTextSearch
import ch.srgssr.pillarbox.backend.db.PaginatedResult
import ch.srgssr.pillarbox.backend.db.SearchableRepository
import ch.srgssr.pillarbox.backend.db.map
import ch.srgssr.pillarbox.backend.db.paginated
import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.persistence.folder.FolderMediaTable
import ch.srgssr.pillarbox.backend.time.toKotlinInstant
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpsertBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
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
) : SearchableRepository<Media, String>(
    db = db,
    table = MediaTable,
    idColumn = MediaTable.id,
    textSearch = FullTextSearch(MediaTable, column = "search_vector"),
  ) {
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

  /**
   * Marks the media item with the given [id] as deleted without removing it from the database.
   *
   * @param id The unique identifier of the media to soft-delete.
   * @return `true` if the media was found and marked as deleted, `false` if it was already deleted or does not exist.
   */
  suspend fun softDelete(id: String): Boolean =
    query {
      MediaTable.update({ (MediaTable.id eq id) and (MediaTable.deleted eq false) }) {
        it[deleted] = true
        it[lastModified] = Clock.System.now().toUtcOffsetDateTime()
      } > 0
    }

  /**
   * Restores a previously soft-deleted media item, making it active again.
   *
   * @param id The unique identifier of the media to restore.
   * @return `true` if the media was found and restored, `false` if it was not deleted or does not exist.
   */
  suspend fun restore(id: String): Boolean =
    query {
      MediaTable.update({ (MediaTable.id eq id) and (MediaTable.deleted eq true) }) {
        it[deleted] = false
        it[lastModified] = Clock.System.now().toUtcOffsetDateTime()
      } > 0
    }

  /**
   * Retrieves a paginated list of non-deleted media items assigned to the given folder.
   *
   * @param folderId The ID of the folder to query.
   * @param limit The maximum number of items to return.
   * @param offset The number of items to skip before returning results.
   * @param filter An optional additional filter predicate.
   * @param sort An optional list of column/order pairs for sorting.
   * @return A [PaginatedResult] containing the matching [Media] items and total count.
   */
  suspend fun findMediaInFolder(
    folderId: String,
    limit: Int = 100,
    offset: Long = 0,
    filter: (() -> Op<Boolean>)? = null,
    sort: List<Pair<Expression<*>, SortOrder>>? = null,
  ): PaginatedResult<Media> =
    query(readOnly = true) {
      MediaTable
        .join(FolderMediaTable, JoinType.INNER, MediaTable.id, FolderMediaTable.mediaId)
        .selectAll()
        .where { (FolderMediaTable.folderId eq folderId) and (MediaTable.deleted eq false) }
        .apply { filter?.let { andWhere(it) } }
        .apply { sort?.let { orderBy(*it.toTypedArray()) } }
        .paginated(limit, offset)
        .map { it.decode() }
    }

  /**
   * Retrieves a page of active (non-deleted) media, narrowed by an optional full-text [query].
   *
   * @param query Optional search text; blank or `null` lists the page unfiltered.
   * @param limit The maximum number of items to return.
   * @param offset The number of items to skip before returning results.
   * @return The matching [Media] items, most relevant first when searching.
   */
  suspend fun findActiveMedia(
    query: String?,
    limit: Int = 100,
    offset: Long = 0,
  ): List<Media> =
    when {
      query.isNullOrBlank() -> getAll(limit, offset, filter = { MediaTable.deleted eq false }).toList()
      else -> search(query, limit, offset, filter = { MediaTable.deleted eq false }).items
    }

  /**
   * Retrieves a paginated list of non-deleted media items that are not assigned to any folder.
   *
   * @param limit The maximum number of items to return.
   * @param offset The number of items to skip before returning results.
   * @param filter An optional additional filter predicate.
   * @param sort An optional list of column/order pairs for sorting.
   * @return A [PaginatedResult] containing the matching [Media] items and total count.
   */
  suspend fun findMediaWithoutFolder(
    limit: Int = 100,
    offset: Long = 0,
    filter: (() -> Op<Boolean>)? = null,
    sort: List<Pair<Expression<*>, SortOrder>>? = null,
  ): PaginatedResult<Media> =
    query(readOnly = true) {
      MediaTable
        .join(FolderMediaTable, JoinType.LEFT, MediaTable.id, FolderMediaTable.mediaId)
        .selectAll()
        .where { FolderMediaTable.mediaId.isNull() and (MediaTable.deleted eq false) }
        .apply { filter?.let { andWhere(it) } }
        .apply { sort?.let { orderBy(*it.toTypedArray()) } }
        .paginated(limit, offset)
        .map { it.decode() }
    }
}
