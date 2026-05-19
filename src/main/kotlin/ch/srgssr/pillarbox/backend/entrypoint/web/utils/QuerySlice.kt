package ch.srgssr.pillarbox.backend.entrypoint.web.utils

import io.ktor.http.Parameters
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
 * Parses `limit` and `offset` query parameters into a [QuerySlice],
 * falling back to defaults on missing or invalid values.
 *
 * @return A [QuerySlice] derived from the `limit` and `offset` query parameters.
 */
fun Parameters.toQuerySlice() =
  QuerySlice(
    limit = get("limit")?.toIntOrNull() ?: 20,
    offset = get("offset")?.toLongOrNull() ?: 0L,
  )
