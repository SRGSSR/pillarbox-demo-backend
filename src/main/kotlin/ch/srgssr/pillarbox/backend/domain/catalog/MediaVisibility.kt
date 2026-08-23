package ch.srgssr.pillarbox.backend.domain.catalog

/**
 * What a caller is allowed to see of the media catalogue.
 */
enum class MediaVisibility {
  /** Not deleted and not past its expiration date. */
  PLAYABLE,

  /** Not deleted; expiration is ignored. */
  ACTIVE,

  /** Soft-deleted media only. */
  DELETED,

  /** No visibility predicate. */
  ANY,
}
