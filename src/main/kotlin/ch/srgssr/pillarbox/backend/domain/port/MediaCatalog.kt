package ch.srgssr.pillarbox.backend.domain.port

import ch.srgssr.pillarbox.backend.domain.catalog.MediaCriteria
import ch.srgssr.pillarbox.backend.domain.catalog.MediaVisibility
import ch.srgssr.pillarbox.backend.domain.catalog.PaginatedResult
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.model.Media

/**
 * Reads and writes the media catalogue.
 */
interface MediaCatalog {
  /**
   * Finds a media item by its identifier, narrowed to the given visibility.
   *
   * @param id The unique identifier of the media.
   * @param visibility What the caller is allowed to see.
   * @return The media if it exists within [visibility], or `null` otherwise.
   */
  suspend fun find(
    id: String,
    visibility: MediaVisibility = MediaVisibility.ANY,
  ): Media?

  /**
   * Retrieves the page of media matching [criteria].
   *
   * @param criteria The catalogue query in domain terms.
   * @param slice The window of the result to return.
   * @return A [PaginatedResult] with the matching media and the total number of matches.
   */
  suspend fun page(
    criteria: MediaCriteria,
    slice: QuerySlice,
  ): PaginatedResult<Media>

  /**
   * Whether a media item exists with this identifier, regardless of visibility.
   *
   * @param id The unique identifier of the media.
   * @return `true` if it exists, `false` otherwise.
   */
  suspend fun exists(id: String): Boolean

  /**
   * Persists or overwrites a media item.
   *
   * @param item The media to save.
   * @return The persisted media.
   */
  suspend fun save(item: Media): Media

  /**
   * Marks a media item as deleted without removing it from the database.
   *
   * @param id The unique identifier of the media to soft-delete.
   * @return `true` if the media was found and marked as deleted, `false` if it was already deleted or does not exist.
   */
  suspend fun softDelete(id: String): Boolean

  /**
   * Restores a previously soft-deleted media item, making it active again.
   *
   * @param id The unique identifier of the media to restore.
   * @return `true` if the media was found and restored, `false` if it was not deleted or does not exist.
   */
  suspend fun restore(id: String): Boolean

  /**
   * Atomically replaces the tags of a media item.
   *
   * @param id The unique identifier of the media to update.
   * @param transform A function from the current tags to the new tags.
   * @return The updated tags, or `null` if the media does not exist.
   */
  suspend fun updateTags(
    id: String,
    transform: (List<String>) -> List<String>,
  ): List<String>?
}
