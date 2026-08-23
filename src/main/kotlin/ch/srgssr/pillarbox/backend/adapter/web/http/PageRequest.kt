package ch.srgssr.pillarbox.backend.adapter.web.http

import io.ktor.http.Parameters
import kotlinx.serialization.Serializable

/**
 * A page-based cursor into a collection.
 *
 * @property page Zero-based page index.
 * @property pageSize Number of items per page.
 * @property limit Alias for [pageSize], for use with repository calls that accept a limit.
 * @property offset Absolute item offset derived from [page] and [pageSize].
 * @property nextPage Convenience value for the following page index, ready to embed in a response.
 */
@Serializable
data class PageRequest(
  val page: Int = 0,
  val pageSize: Int = 15,
) {
  val limit: Int get() = pageSize
  val offset: Long get() = (page * pageSize).toLong()
  val nextPage: Int get() = page + 1
}

/**
 * Parses `page` and `pageSize` query parameters into a [PageRequest],
 * falling back to defaults on missing or invalid values.
 *
 * @return A [PageRequest] derived from the `page` and `pageSize` query parameters.
 */
fun Parameters.toPageRequest() =
  PageRequest(
    page = get("page")?.toIntOrNull() ?: 0,
    pageSize = get("pageSize")?.toIntOrNull() ?: 15,
  )
