package ch.srgssr.pillarbox.backend.domain.catalog

import kotlinx.serialization.Serializable

/**
 * A window into a collection defined by an item count and a starting offset.
 *
 * @property limit Maximum number of items to return.
 * @property offset Zero-based index of the first item to return.
 */
@Serializable
data class QuerySlice(
  val limit: Int = 20,
  val offset: Long = 0L,
)

/**
 * One page of a collection, together with the total number of matches.
 *
 * @param T The type of the paged items.
 * @property items The slice of data for the current page.
 * @property totalCount The total number of items matching the query across all pages.
 * @property limit The maximum number of items requested for this page.
 * @property offset The starting point of this page in the total result.
 */
data class PaginatedResult<T>(
  val items: List<T>,
  val totalCount: Long,
  val limit: Int,
  val offset: Long,
)

/**
 * Transforms the items in this [PaginatedResult] while preserving the paging metadata.
 *
 * @param transform Mapping applied to each item.
 * @return A [PaginatedResult] of the transformed items with unchanged counts and offsets.
 */
fun <T, R> PaginatedResult<T>.map(transform: (T) -> R) =
  PaginatedResult(
    items = items.map(transform),
    totalCount = totalCount,
    limit = limit,
    offset = offset,
  )
