package ch.srgssr.pillarbox.backend.adapter.persistence.media

import ch.srgssr.pillarbox.backend.adapter.persistence.folder.FolderMediaTable
import ch.srgssr.pillarbox.backend.domain.catalog.FolderScope
import ch.srgssr.pillarbox.backend.domain.catalog.MediaVisibility
import ch.srgssr.pillarbox.backend.time.toUtcOffsetDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.time.Instant

/**
 * Translates a [MediaVisibility] into its SQL mirror over [MediaTable].
 *
 * Must agree with [ch.srgssr.pillarbox.backend.domain.model.Media.isPlayable];
 * a repository test pins the two together.
 *
 * @param now The instant playability is evaluated at.
 * @return The `WHERE` predicate expressing this visibility.
 */
internal fun MediaVisibility.toPredicate(now: Instant): Op<Boolean> =
  when (this) {
    MediaVisibility.PLAYABLE -> {
      MediaVisibility.ACTIVE.toPredicate(now) and
        (MediaTable.expiresAt.isNull() or (MediaTable.expiresAt greater now.toUtcOffsetDateTime()))
    }

    MediaVisibility.ACTIVE -> {
      MediaTable.deleted eq false
    }

    MediaVisibility.DELETED -> {
      MediaTable.deleted eq true
    }

    MediaVisibility.ANY -> {
      Op.TRUE
    }
  }

/**
 * Translates a [FolderScope] into a predicate on the media's folder assignment.
 *
 * @return The `WHERE` predicate expressing this scope via the [FolderMediaTable] junction.
 */
internal fun FolderScope.toPredicate(): Op<Boolean> =
  when (this) {
    FolderScope.Anywhere -> {
      Op.TRUE
    }

    FolderScope.Unassigned -> {
      MediaTable.id notInSubQuery FolderMediaTable.select(FolderMediaTable.mediaId)
    }

    is FolderScope.In -> {
      MediaTable.id inSubQuery
        FolderMediaTable.select(FolderMediaTable.mediaId).where { FolderMediaTable.folderId eq folderId }
    }
  }
