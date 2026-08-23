package ch.srgssr.pillarbox.backend.adapter.persistence.folder

import ch.srgssr.pillarbox.backend.adapter.persistence.ExposedRepository
import ch.srgssr.pillarbox.backend.domain.model.FolderPermission
import ch.srgssr.pillarbox.backend.domain.model.PermissionSubject
import ch.srgssr.pillarbox.backend.domain.model.Role.Companion.toRole
import ch.srgssr.pillarbox.backend.domain.port.FolderGrants
import ch.srgssr.pillarbox.backend.time.toKotlinInstant
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpsertBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * Repository responsible for the persistence and retrieval of [FolderPermission] grants using Exposed.
 *
 * This implementation maps the [FolderPermission] domain model to the [FolderPermissionTable] schema.
 * Grant inheritance is resolved through the `v_folder_ancestors` recursive view ([FolderAncestorView]),
 * so the grants effective on a folder are read in a single query regardless of folder depth.
 *
 * @param db The [Database] instance used for all transactions.
 */
class FolderPermissionRepository(
  db: Database,
) : ExposedRepository<FolderPermission, String>(
    db = db,
    table = FolderPermissionTable,
    idColumn = FolderPermissionTable.id,
  ),
  FolderGrants {
  /**
   * Decodes a [ResultRow] from the [FolderPermissionTable] into a [FolderPermission] domain object.
   */
  override fun ResultRow.decode() =
    FolderPermission(
      id = this[FolderPermissionTable.id],
      folderId = this[FolderPermissionTable.folderId],
      subject = decodeSubject(),
      canWrite = this[FolderPermissionTable.canWrite],
      createdAt = this[FolderPermissionTable.createdAt].toKotlinInstant(),
    )

  /**
   * Encodes a [FolderPermission] domain object into an [UpdateBuilder] for inserts.
   */
  override fun Table.encode(
    builder: UpdateBuilder<*>,
    item: FolderPermission,
  ) {
    builder[FolderPermissionTable.id] = item.id
    builder[FolderPermissionTable.folderId] = item.folderId
    builder[FolderPermissionTable.oidcSub] = (item.subject as? PermissionSubject.ForUser)?.oidcSub
    builder[FolderPermissionTable.teamId] = (item.subject as? PermissionSubject.ForTeam)?.teamId
    builder[FolderPermissionTable.role] = (item.subject as? PermissionSubject.ForRole)?.role?.key
    builder[FolderPermissionTable.canWrite] = item.canWrite
    builder[FolderPermissionTable.createdAt] = item.createdAt.toUtcOffsetDateTime()
  }

  /**
   * Encodes a [FolderPermission] for updates, leaving the immutable [FolderPermissionTable.createdAt]
   * and subject columns untouched so re-saving a grant only adjusts its access level.
   */
  override fun encodeOnUpdate(item: FolderPermission): (UpsertBuilder.(UpdateStatement) -> Unit) =
    {
      it[FolderPermissionTable.canWrite] = item.canWrite
    }

  /**
   * Upserts a grant on its natural key — the unique `(folder_id, oidc_sub, team_id, role)` tuple —
   * rather than the surrogate [FolderPermissionTable.id]. Re-granting a subject that already has a
   * grant (e.g. toggling the editor role) therefore updates its access level in place instead of
   * inserting a conflicting row.
   *
   * @param item The grant to insert or update.
   * @return The persisted grant.
   */
  override suspend fun save(item: FolderPermission): FolderPermission =
    query {
      val statement =
        FolderPermissionTable.upsert(
          FolderPermissionTable.folderId,
          FolderPermissionTable.oidcSub,
          FolderPermissionTable.teamId,
          FolderPermissionTable.role,
          onUpdate = encodeOnUpdate(item),
        ) { encode(it, item) }
      FolderPermissionTable
        .selectAll()
        .where { FolderPermissionTable.id eq statement[FolderPermissionTable.id] }
        .single()
        .decode()
    }

  /**
   * Finds the folder's own grant for [subject], looking only at the folder itself and matching the
   * subject columns directly, so editing or revoking a single grant costs one indexed lookup
   * instead of reading the whole ancestor chain.
   *
   * @param folderId The folder whose own grant is searched.
   * @param subject The subject the grant must apply to.
   * @return The matching own grant, or `null` when the folder has no own grant for the subject.
   */
  override suspend fun findGrant(
    folderId: String,
    subject: PermissionSubject,
  ): FolderPermission? =
    findOne {
      (FolderPermissionTable.folderId eq folderId) and
        when (subject) {
          is PermissionSubject.ForUser -> FolderPermissionTable.oidcSub eq subject.oidcSub
          is PermissionSubject.ForTeam -> FolderPermissionTable.teamId eq subject.teamId
          is PermissionSubject.ForRole -> FolderPermissionTable.role eq subject.role.key
        }
    }

  /**
   * Retrieves all grants attached to the given folder or any of its ancestors, in a single query
   * via the [FolderAncestorView].
   *
   * An empty result means the folder is unrestricted.
   *
   * @param folderId The ID of the folder whose effective grants should be retrieved.
   * @return The grants on the folder and its ancestors, each carrying the folder it is attached to.
   */
  override suspend fun findGrantsInChain(folderId: String): List<FolderPermission> =
    query(readOnly = true) {
      grantsJoinedWithAncestors()
        .where { FolderAncestorView.descendantId eq folderId }
        .map { it.decode() }
    }

  /**
   * Retrieves the grants effective on each of the given folders, grouped by folder, in a single query.
   *
   * Each value holds the grants attached to that folder or any of its ancestors. Folders with no
   * effective grant are absent from the map and should be treated as unrestricted.
   *
   * @param folderIds The folders whose effective grants should be retrieved.
   * @return A map from folder id to the grants effective on it.
   */
  override suspend fun findGrantsInChains(folderIds: Collection<String>): Map<String, List<FolderPermission>> =
    query(readOnly = true) {
      if (folderIds.isEmpty()) {
        emptyMap()
      } else {
        grantsJoinedWithAncestors()
          .where { FolderAncestorView.descendantId inList folderIds }
          .groupBy({ it[FolderAncestorView.descendantId] }) { it.decode() }
      }
    }

  private fun grantsJoinedWithAncestors() =
    FolderPermissionTable
      .join(FolderAncestorView, JoinType.INNER) { FolderPermissionTable.folderId eq FolderAncestorView.id }
      .selectAll()

  private fun ResultRow.decodeSubject(): PermissionSubject {
    val oidcSub = this[FolderPermissionTable.oidcSub]
    val teamId = this[FolderPermissionTable.teamId]
    val role = this[FolderPermissionTable.role]

    return when {
      oidcSub != null -> {
        PermissionSubject.ForUser(oidcSub)
      }

      teamId != null -> {
        PermissionSubject.ForTeam(teamId)
      }

      else -> {
        PermissionSubject.ForRole(
          requireNotNull(role?.toRole()) { "Grant ${this[FolderPermissionTable.id]} has no valid subject" },
        )
      }
    }
  }
}
