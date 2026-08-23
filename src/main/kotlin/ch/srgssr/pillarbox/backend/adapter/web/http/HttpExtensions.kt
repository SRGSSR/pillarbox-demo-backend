package ch.srgssr.pillarbox.backend.adapter.web.http

import io.ktor.http.Headers
import io.ktor.http.Parameters

/**
 * Parses a header value as a prioritized list of tokens, following standard HTTP conventions.
 *
 * Supports both comma-separated values within a single header and multiple headers with the
 * same name.
 *
 * @param name The header name to look up.
 *
 * @return A list of trimmed, non-empty tokens in the order they were declared, or an empty list
 *         if the header is absent or contains no valid tokens.
 */
fun Headers.parseHeaderList(name: String): List<String> =
  getAll(name)
    ?.flatMap { it.split(",") }
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: emptyList()

/**
 * Parses a query parameter value as a prioritized list of tokens.
 *
 * Supports both comma-separated values within a single parameter and repeated parameters with
 * the same name (e.g. `?param=a&param=b`).
 *
 * @param name The query parameter name to look up.
 *
 * @return A list of trimmed, non-empty tokens in the order they were declared, or an empty list
 *         if the parameter is absent or contains no valid tokens.
 */
fun Parameters.parseParamList(name: String): List<String> =
  getAll(name)
    ?.flatMap { it.split(",") }
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: emptyList()
