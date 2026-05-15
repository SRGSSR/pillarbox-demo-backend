package ch.srgssr.pillarbox.backend.entrypoint.web.utils

import io.ktor.http.Parameters
import kotlinx.serialization.Serializable

/**
 * Pagination parameters extracted from query strings.
 *
 * @property limit Maximum number of items to return. Defaults to 20.
 * @property offset Number of items to skip before the first result. Defaults to 0.
 */
@Serializable
data class PageRequest(
  val limit: Int = 20,
  val offset: Long = 0L,
)

/**
 * Extracts pagination parameters from the query string, falling back to
 * [PageRequest] defaults for missing or malformed values.
 */
fun Parameters.toPageRequest() =
  PageRequest(
    limit = get("limit")?.toIntOrNull() ?: 20,
    offset = get("offset")?.toLongOrNull() ?: 0L,
  )
