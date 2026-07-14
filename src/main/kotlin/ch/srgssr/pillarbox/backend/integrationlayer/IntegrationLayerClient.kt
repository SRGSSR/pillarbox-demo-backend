package ch.srgssr.pillarbox.backend.integrationlayer

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess

/**
 * Client for the SRG SSR Integration Layer public API.
 *
 * @property httpClient The shared HTTP client used to perform requests.
 * @property config Connection parameters for the Integration Layer.
 */
class IntegrationLayerClient(
  private val httpClient: HttpClient,
  private val config: IntegrationLayerConfig,
) {
  /**
   * Fetches the media composition identified by the given URN.
   *
   * @param urn The URN of the media (e.g., `urn:rts:video:3608506`).
   *
   * @return The decoded composition, or null when the URN is not found.
   */
  suspend fun findMediaComposition(urn: String): MediaComposition? {
    val response =
      httpClient.get(config.baseUrl) {
        url.appendPathSegments(listOf("integrationlayer", "2.0", "mediaComposition", "byUrn", urn), encodeSlash = true)
      }

    return if (response.status.isSuccess()) response.body() else null
  }
}
