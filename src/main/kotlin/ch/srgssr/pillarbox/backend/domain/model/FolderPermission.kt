package ch.srgssr.pillarbox.backend.domain.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The subject a [FolderPermission] applies to. Exactly one identity is carried per grant.
 */
sealed interface PermissionSubject {
  /** Grant for a single user identified by [oidcSub]. */
  data class ForUser(
    val oidcSub: String,
  ) : PermissionSubject

  /** Grant for all members of the team identified by [teamId]. */
  data class ForTeam(
    val teamId: String,
  ) : PermissionSubject

  /**
   * Grant for all users holding [role], e.g. to re-open a folder
   * to all editors inside an otherwise restricted subtree.
   */
  data class ForRole(
    val role: Role,
  ) : PermissionSubject
}

/**
 * An explicit access grant on a folder.
 *
 * A folder without grants in its ancestor chain is unrestricted: every editor may write.
 * The first grant on a folder restricts it, and from then on only granted subjects
 * (and administrators) may write to it and its descendants. Grants are inherited
 * downwards additively.
 *
 * Reading is always open to every authenticated user, so [canWrite] is the only level
 * a grant carries: `true` grants write access to its subject, `false` records the
 * subject without widening write access.
 *
 * @property id Unique identifier of the grant.
 * @property folderId The folder this grant is attached to.
 * @property subject Who the grant applies to.
 * @property canWrite Whether the grant confers write access to its subject.
 * @property createdAt Timestamp of the grant creation.
 */
@OptIn(ExperimentalUuidApi::class)
data class FolderPermission(
  val id: String = Uuid.random().toString(),
  val folderId: String,
  val subject: PermissionSubject,
  val canWrite: Boolean = true,
  val createdAt: Instant = Clock.System.now(),
)
