package ch.srgssr.pillarbox.backend.domain.catalog

/**
 * Where in the folder tree a query looks.
 */
sealed interface FolderScope {
  /** The whole tree, regardless of folder assignment. */
  object Anywhere : FolderScope

  /** Items without a folder assignment; for folders themselves, the root level. */
  object Unassigned : FolderScope

  /**
   * Items assigned to a single folder.
   *
   * @property folderId The folder the query is limited to.
   */
  data class In(
    val folderId: String,
  ) : FolderScope
}

/**
 * A catalogue query in domain terms. Callers describe what they want;
 * the persistence adapter decides the SQL.
 *
 * @property visibility What the caller is allowed to see.
 * @property scope Where in the folder tree the query looks.
 * @property text Optional full-text search input; `null` or blank lists without searching.
 */
data class MediaCriteria(
  val visibility: MediaVisibility = MediaVisibility.ACTIVE,
  val scope: FolderScope = FolderScope.Anywhere,
  val text: String? = null,
)
