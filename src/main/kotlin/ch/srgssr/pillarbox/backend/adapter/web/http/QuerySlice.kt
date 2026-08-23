package ch.srgssr.pillarbox.backend.adapter.web.http

import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import io.ktor.http.Parameters

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
