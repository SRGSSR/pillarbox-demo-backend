package ch.srgssr.pillarbox.backend.application.media

import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.port.MediaCatalog
import ch.srgssr.pillarbox.backend.domain.port.MediaImportSource

/**
 * Imports a media item from the external catalogue and reports whether it
 * already exists in the local one.
 *
 * @property importSource Port used to fetch the media by URN.
 * @property mediaCatalog Port used to check for an existing local copy.
 */
class ImportMediaFromUrn(
  private val importSource: MediaImportSource,
  private val mediaCatalog: MediaCatalog,
) {
  /**
   * The outcome of a successful import lookup.
   *
   * @property media The media fetched from the external catalogue.
   * @property exists Whether a media with the same id is already in the local catalogue.
   */
  data class Import(
    val media: Media,
    val exists: Boolean,
  )

  /**
   * Fetches the media identified by [urn] and checks for a local copy.
   *
   * @param urn The URN of the media to import.
   * @return The import outcome, or `null` when the URN is unknown or the source is unreachable.
   */
  suspend operator fun invoke(urn: String): Import? =
    importSource.findByUrn(urn)?.let { Import(it, mediaCatalog.exists(it.id)) }
}
