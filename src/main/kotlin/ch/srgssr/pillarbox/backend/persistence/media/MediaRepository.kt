package ch.srgssr.pillarbox.backend.persistence.media

import ch.srgssr.pillarbox.backend.domain.model.Media
import kotlinx.coroutines.flow.Flow

/**
 * Repository responsible for the persistence and retrieval of [Media] entities.
 *
 * This class serves as the bridge between the domain layer and the data storage.
 */
@SuppressWarnings("UNUSED_PARAMETER")
class MediaRepository {
  /**
   * Retrieves a paginated stream of all available media resources.
   *
   * @param limit The maximum number of items to return.
   * @param offset The number of items to skip for pagination.
   *
   * @return A [Flow] emitting the collection of [Media] objects.
   */
  suspend fun getAll(
    limit: Int,
    offset: Long,
  ): Flow<Media> {
    TODO("Not yet implemented")
  }

  /**
   * Finds a specific media resource by its unique identifier.
   *
   * @param id The unique identifier of the media.
   * @return The [Media] object if found, or null if no match exists.
   */
  suspend fun find(id: String): Media? {
    TODO("Not yet implemented")
  }

  /**
   * Persists or overwrites a media resource.
   *
   * @param id The unique identifier to associate with this media.
   * @param media The media entity to save.
   */
  suspend fun save(
    id: String,
    media: Media,
  ) {
    TODO("Not yet implemented")
  }

  /**
   * Atomically updates the tags of a specific media resource.
   *
   * This method retrieves the existing tags, applies the [transform] function,
   * and persists the result.
   *
   * @param id The unique identifier of the media to update.
   * @param transform A lambda that receives the current list of tags and returns the new list.
   * @return The updated list of tags if the media was found and updated, or null if the media does not exist.
   */
  suspend fun updateTags(
    id: String,
    transform: (List<String>) -> List<String>,
  ): List<String>? {
    TODO("Not yet implemented")
  }

  /**
   * Deletes a media resource from the persistence layer.
   *
   * @param id The unique identifier of the media to delete.
   * @return true if the media was successfully deleted, false if it did not exist.
   */
  suspend fun delete(id: String): Boolean {
    TODO("Not yet implemented")
  }
}
