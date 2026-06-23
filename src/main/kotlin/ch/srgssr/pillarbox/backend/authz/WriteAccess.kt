package ch.srgssr.pillarbox.backend.authz

import ch.srgssr.pillarbox.backend.auth.user
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.koin.ktor.ext.get

/**
 * The [PermissionChecker] for this call, resolved from the application's Koin container so it does
 * not need to be threaded through every route.
 */
val ApplicationCall.permissionChecker: PermissionChecker
  get() = application.get()

/**
 * Runs [block] only if the authenticated user may write every one of the given [folderIds],
 * otherwise responds [HttpStatusCode.Forbidden] and skips it.
 *
 * A `null` id denotes the unrestricted root scope, so the block runs.
 *
 * @param folderIds The folders that must be writable; pass several to require write access to all.
 * @param block The work to perform when access is granted.
 */
suspend fun RoutingContext.withFolderWrite(
  vararg folderIds: String?,
  block: suspend () -> Unit,
) {
  if (folderIds.all { call.permissionChecker.canWriteFolder(call.user, it) }) {
    block()
  } else {
    call.respond(HttpStatusCode.Forbidden)
  }
}

/**
 * Runs [block] only if the authenticated user may write the media identified by [mediaId],
 * otherwise responds [HttpStatusCode.Forbidden] and skips it.
 *
 * A `null` id means there is no existing media to protect (e.g. creating a new item), so the
 * block runs.
 *
 * @param mediaId The media to protect, or `null` when there is nothing to check.
 * @param block The work to perform when access is granted.
 */
suspend fun RoutingContext.withMediaWrite(
  mediaId: String?,
  block: suspend () -> Unit,
) {
  if (mediaId == null || call.permissionChecker.canWriteMedia(call.user, mediaId)) {
    block()
  } else {
    call.respond(HttpStatusCode.Forbidden)
  }
}
