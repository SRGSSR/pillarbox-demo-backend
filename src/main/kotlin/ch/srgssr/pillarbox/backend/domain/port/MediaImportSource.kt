package ch.srgssr.pillarbox.backend.domain.port

import ch.srgssr.pillarbox.backend.domain.model.Media

/**
 * An external catalogue media can be imported from.
 */
interface MediaImportSource {
  /**
   * Fetches the media identified by a URN from the external catalogue.
   *
   * @param urn The URN of the media (e.g. `urn:rts:video:3608506`).
   * @return The mapped media, or `null` when the URN is unknown or the source is unreachable.
   */
  suspend fun findByUrn(urn: String): Media?
}
