package ch.srgssr.pillarbox.backend.adapter.web.http

import ch.srgssr.pillarbox.backend.domain.catalog.FolderScope
import ch.srgssr.pillarbox.backend.domain.catalog.MediaVisibility
import io.ktor.http.Parameters

/**
 * Parses the `visibility` query parameter into a [MediaVisibility].
 *
 * Accepted values are `active` and `deleted`. An absent parameter means [MediaVisibility.ANY]:
 * callers narrow the listing explicitly.
 *
 * @return The requested [MediaVisibility], or `null` if the value is not one of the accepted values.
 */
fun Parameters.toMediaVisibility(): MediaVisibility? =
  when (get("visibility")) {
    null -> MediaVisibility.ANY
    "active" -> MediaVisibility.ACTIVE
    "deleted" -> MediaVisibility.DELETED
    else -> null
  }

/**
 * Parses the `scope` query parameter into a [FolderScope].
 *
 * Accepted values are `all` and `unassigned`. An absent parameter means [FolderScope.Anywhere].
 *
 * @return The requested [FolderScope], or `null` if the value is not one of the accepted values.
 */
fun Parameters.toFolderScope(): FolderScope? =
  when (get("scope")) {
    null, "all" -> FolderScope.Anywhere
    "unassigned" -> FolderScope.Unassigned
    else -> null
  }
