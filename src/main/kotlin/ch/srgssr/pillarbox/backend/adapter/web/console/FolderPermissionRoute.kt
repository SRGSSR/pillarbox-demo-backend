package ch.srgssr.pillarbox.backend.adapter.web.console

import ch.srgssr.pillarbox.backend.adapter.web.http.withFolderWrite
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
import ch.srgssr.pillarbox.backend.domain.model.FolderPermission
import ch.srgssr.pillarbox.backend.domain.model.PermissionSubject
import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.port.FolderCatalog
import ch.srgssr.pillarbox.backend.domain.port.FolderGrants
import ch.srgssr.pillarbox.backend.domain.port.TeamCatalog
import ch.srgssr.pillarbox.backend.domain.port.UserCatalog
import ch.srgssr.pillarbox.backend.log.info
import ch.srgssr.pillarbox.backend.log.logger
import io.ktor.http.HttpStatusCode
import io.ktor.server.htmx.hx
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.util.getOrFail
import io.ktor.utils.io.ExperimentalKtorApi

private object ConsolePermissionRoute

private val logger = ConsolePermissionRoute.logger()

/**
 * Registers the read-only HTMX fragments backing the folder permissions dialog.
 *
 * @param folderCatalog Repository used to resolve folders.
 * @param folderGrants Repository used to read folder grants.
 * @param userCatalog Repository used to resolve and search users.
 * @param teamCatalog Repository used to resolve and search teams.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.folderPermissionFragments(
  folderCatalog: FolderCatalog,
  folderGrants: FolderGrants,
  userCatalog: UserCatalog,
  teamCatalog: TeamCatalog,
) {
  hx.get("fragments/folder-permissions") {
    val folderId = call.queryParameters.getOrFail("folderId")
    val folder = folderCatalog.find(folderId) ?: return@get call.respond(HttpStatusCode.NotFound)
    withFolderWrite(folderId) {
      call.respondWithContext("modules/home/fragments/folder-permissions.fragment.peb", mapOf("folder" to folder))
    }
  }

  hx.get("fragments/folder-permissions-list") {
    val folderId = call.queryParameters.getOrFail("folderId")
    val folder = folderCatalog.find(folderId) ?: return@get call.respond(HttpStatusCode.NotFound)
    withFolderWrite(folderId) {
      val rows = permissionRows(folderId, folderGrants, userCatalog, teamCatalog)
      call.respondWithContext(
        "modules/home/fragments/folder-permissions-list.fragment.peb",
        mapOf("folder" to folder, "rows" to rows),
      )
    }
  }

  hx.get("fragments/subject-options") {
    val query = call.queryParameters["subject"].orEmpty().trim()
    call.respondWithContext(
      "shared/fragments/datalist-options.fragment.peb",
      mapOf("options" to searchSubjects(query, userCatalog, teamCatalog)),
    )
  }
}

/**
 * Registers the HTMX actions that create, update and revoke a folder's grants.
 *
 * @param folderCatalog Repository used to resolve folders.
 * @param folderGrants Repository used to persist folder grants.
 * @param userCatalog Repository used to resolve and validate users.
 * @param teamCatalog Repository used to resolve and validate teams.
 */
@OptIn(ExperimentalKtorApi::class)
@Suppress("LongMethod")
fun Route.folderPermissionActions(
  folderCatalog: FolderCatalog,
  folderGrants: FolderGrants,
  userCatalog: UserCatalog,
  teamCatalog: TeamCatalog,
) {
  hx.post("actions/folder/{id}/permission") {
    val id = call.parameters.getOrFail("id")
    val folder = folderCatalog.find(id) ?: return@post call.respond(HttpStatusCode.NotFound)
    val params = call.receiveParameters()
    val canWrite = params["level"] == "write"

    withFolderWrite(id) {
      val resolved = resolveSubject(params["subjectRef"].orEmpty(), userCatalog, teamCatalog)
      if (resolved == null) {
        call.respond(HttpStatusCode.UnprocessableEntity, "Select a user or team from the suggestions")
        return@withFolderWrite
      }

      logger.info { "Granting ${if (canWrite) "write" else "view"} on folder $id to ${resolved.subject}" }
      val key = subjectKey(resolved.subject)
      val replacesExistingRow = folderGrants.findGrantsInChain(id).any { subjectKey(it.subject) == key }
      folderGrants.save(FolderPermission(folderId = id, subject = resolved.subject, canWrite = canWrite))
      val row = PermissionRow(key, resolved.label, canWrite, inherited = false)
      call.respondWithContext(
        "modules/home/fragments/permission-row.fragment.peb",
        mapOf("folder" to folder, "row" to row, "oob" to replacesExistingRow),
      )
    }
  }

  hx.patch("actions/folder/{id}/permission/{subject}") {
    val id = call.parameters.getOrFail("id")
    val subjectRef = call.parameters.getOrFail("subject")
    folderCatalog.find(id) ?: return@patch call.respond(HttpStatusCode.NotFound)
    val canWrite = call.receiveParameters()["level"] == "write"

    withFolderWrite(id) {
      val subject =
        if (subjectRef == "editor") {
          PermissionSubject.ForRole(Role.WRITE)
        } else {
          subjectRef.toSubject()?.takeIf { folderGrants.findGrant(id, it) != null }
        }
      if (subject == null) {
        call.respond(HttpStatusCode.NotFound)
      } else {
        folderGrants.save(FolderPermission(folderId = id, subject = subject, canWrite = canWrite))
        call.respond(HttpStatusCode.NoContent)
      }
    }
  }

  hx.delete("actions/folder/{id}/permission/{subject}") {
    val id = call.parameters.getOrFail("id")
    val subjectRef = call.parameters.getOrFail("subject")

    withFolderWrite(id) {
      val grant = subjectRef.toSubject()?.let { folderGrants.findGrant(id, it) }
      if (grant == null) {
        call.respond(HttpStatusCode.NotFound)
        return@withFolderWrite
      }

      logger.info { "Revoking ${grant.subject} on folder $id" }
      folderGrants.delete(grant.id)

      val inherited =
        folderGrants.findGrantsInChain(id).firstOrNull { subjectKey(it.subject) == subjectRef }
      val folder = inherited?.let { folderCatalog.find(id) }
      when {
        inherited == null -> {
          call.respond(HttpStatusCode.NoContent)
        }

        folder == null -> {
          call.respond(HttpStatusCode.NotFound)
        }

        else -> {
          val label = resolveSubject(subjectRef, userCatalog, teamCatalog)?.label ?: subjectRef
          val row = PermissionRow(subjectRef, label, inherited.canWrite, inherited = true)
          call.response.headers.append("HX-Reswap", "outerHTML")
          call.respondWithContext(
            "modules/home/fragments/permission-row.fragment.peb",
            mapOf("folder" to folder, "row" to row, "oob" to false),
          )
        }
      }
    }
  }
}

/**
 * Searches users and teams with the same query and merges them into one name-sorted suggestion list.
 *
 * @param query The text typed into the search box; blank lists the most recently updated subjects.
 * @param userCatalog Repository used to search users by display name.
 * @param teamCatalog Repository used to search teams by name.
 * @param limit The maximum number of suggestions to return.
 * @return Up to [limit] options, each carrying a `User`/`Team` label and a `type:id` reference.
 */
private suspend fun searchSubjects(
  query: String,
  userCatalog: UserCatalog,
  teamCatalog: TeamCatalog,
  limit: Int = 20,
): List<DatalistOption> {
  val users =
    userCatalog
      .page(query, QuerySlice(limit = limit))
      .items
      .map { DatalistOption(it.displayName, "User", "user:${it.oidcSub}") }
  val teams =
    teamCatalog
      .page(query, QuerySlice(limit = limit))
      .items
      .map { DatalistOption(it.name, "Team", "team:${it.id}") }
  return (teams + users).sortedBy { it.value.lowercase() }.take(limit)
}

/** A subject reference resolved against the directory, carrying the display name for its first row. */
private data class ResolvedSubject(
  val subject: PermissionSubject,
  val label: String,
)

/**
 * Resolves a `user:id`/`team:id` reference to an existing subject, fetching its display name in the
 * same lookup that confirms it exists.
 *
 * @param ref The `type:id` reference to resolve.
 * @param userCatalog Repository used to resolve a referenced user.
 * @param teamCatalog Repository used to resolve a referenced team.
 * @return The subject with its label, or `null` when the reference is malformed or unknown.
 */
private suspend fun resolveSubject(
  ref: String,
  userCatalog: UserCatalog,
  teamCatalog: TeamCatalog,
): ResolvedSubject? =
  when (val subject = ref.toSubject()) {
    is PermissionSubject.ForUser -> {
      userCatalog
        .find(
          subject.oidcSub,
        )?.let { ResolvedSubject(subject, it.displayName) }
    }

    is PermissionSubject.ForTeam -> {
      teamCatalog.find(subject.teamId)?.let { ResolvedSubject(subject, it.name) }
    }

    else -> {
      null
    }
  }

/**
 * Parses a `user:<id>` or `team:<id>` reference into a [PermissionSubject].
 *
 * @receiver The `type:id` reference to parse.
 * @return The subject, or `null` when the receiver is not a user or team reference.
 */
private fun String.toSubject(): PermissionSubject? {
  val (type, id) = split(":", limit = 2).takeIf { it.size == 2 } ?: return null
  return when (type) {
    "user" -> PermissionSubject.ForUser(id)
    "team" -> PermissionSubject.ForTeam(id)
    else -> null
  }
}
