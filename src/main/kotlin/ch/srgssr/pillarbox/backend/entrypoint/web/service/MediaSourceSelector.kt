package ch.srgssr.pillarbox.backend.entrypoint.web.service

import ch.srgssr.pillarbox.backend.domain.model.DrmConfig
import ch.srgssr.pillarbox.backend.domain.model.MediaSource
import ch.srgssr.pillarbox.backend.entrypoint.web.utils.DrmSystems

/**
 * Selects the best [MediaSource] from a list given a client's stream and DRM capabilities.
 *
 * The selector is built once per request and encapsulates all matching and ordering logic.
 * Invoke [select] with the available sources to obtain the chosen [Selection].
 *
 * **Selection rules (in priority order):**
 * 1. The source's MIME type must appear in [mimeTypes].
 * 2. The source must be unprotected, or have at least one DRM config compatible with [drmPreferences].
 * 3. DRM-protected sources rank above unprotected ones (when DRM preferences are expressed).
 * 4. Lower index in [mimeTypes] = higher MIME-type priority.
 * 5. Lower index in [drmPreferences] = higher DRM priority.
 *
 * @property mimeTypes Prioritised list of accepted MIME types (e.g. "application/dash+xml").
 * @property drmPreferences Prioritised list of accepted DRM preferences.
 */
class MediaSourceSelector(
  private val mimeTypes: List<String>,
  private val drmPreferences: List<DrmPreference>,
) {
  /**
   * The outcome of a successful source selection.
   *
   * @property source The winning [MediaSource].
   * @property drm The single [DrmConfig] chosen from [source], or `null` for unprotected sources.
   */
  data class Selection(
    val source: MediaSource,
    val drm: DrmConfig?,
  )

  /**
   * Selects the best source from [sources].
   *
   * Filters out ineligible sources, strips incompatible DRM configs from the remaining ones,
   * sorts by the [selection rules][MediaSourceSelector], and returns the top candidate.
   *
   * @param sources The available media sources to choose from.
   * @return The [Selection] for the highest-priority eligible source, or `null` if none qualify.
   */
  fun select(sources: List<MediaSource>): Selection? =
    sources
      .filter { it.isEligible() }
      .map { it.retainCompatibleDrm() }
      .sortedWith(selectionOrder())
      .firstOrNull()
      ?.let { source -> Selection(source, source.preferredDrm()) }

  /**
   * Checks whether this source passes all eligibility criteria:
   * its MIME type must be accepted and its DRM protection must be compatible (or absent).
   */
  private fun MediaSource.isEligible(): Boolean = matchesMimeType() && isDrmCompatible()

  /**
   * Returns `true` if this source's [mimeType][MediaSource.mimeType] matches
   * any entry in the accepted [mimeTypes] list (case-insensitive).
   */
  private fun MediaSource.matchesMimeType(): Boolean = mimeTypes.any { mimeType?.equals(it, ignoreCase = true) == true }

  /**
   * Returns `true` if this source is either unprotected (no DRM configs)
   * or has at least one [DrmConfig] compatible with the client's [drmPreferences].
   */
  private fun MediaSource.isDrmCompatible(): Boolean =
    drmConfigs.isEmpty() || drmConfigs.any { config -> drmPreferences.any { it.isCompatibleWith(config) } }

  /**
   * Checks whether this [DrmPreference] is compatible with the given [config].
   *
   * Compatibility requires matching key systems. When both the preference's [DrmPreference.maxSecurityLevel]
   * and the config's [DrmConfig.securityLevel] are present, the client's level must be at least as strong
   * as the level required by the config (verified via [SecurityLevels.isCompatible]).
   *
   * @param config The DRM configuration to test against.
   * @return `true` if this preference can satisfy [config].
   */
  private fun DrmPreference.isCompatibleWith(config: DrmConfig): Boolean =
    when {
      config.keySystem != keySystem -> false
      (maxSecurityLevel == null || config.securityLevel == null) -> true
      else -> SecurityLevels.isCompatible(config.keySystem, maxSecurityLevel, config.securityLevel)
    }

  /**
   * Returns a copy of this source containing only the [DrmConfig] entries
   * that are compatible with the client's [drmPreferences].
   */
  private fun MediaSource.retainCompatibleDrm(): MediaSource =
    copy(drmConfigs = drmConfigs.filter { config -> drmPreferences.any { it.isCompatibleWith(config) } })

  /**
   * Builds the [Comparator] used to rank eligible sources.
   *
   * Sources are compared by:
   * 1. **DRM presence** – protected sources rank first over unprotected ones.
   * 2. **MIME-type priority** – lower index in [mimeTypes] is better.
   * 3. **DRM priority** – lowest compatible index in [drmPreferences] wins.
   */
  private fun selectionOrder(): Comparator<MediaSource> =
    compareBy(
      { if (it.drmConfigs.isEmpty()) 1 else 0 },
      { mimeTypes.indexOfFirst { mt -> it.mimeType?.equals(mt, ignoreCase = true) == true } },
      { it.bestDrmPriority() },
    )

  /**
   * Returns the best (lowest) DRM preference index across all compatible [DrmConfig] entries
   * of this source, or [Int.MAX_VALUE] if none match.
   */
  private fun MediaSource.bestDrmPriority(): Int =
    drmConfigs.minOfOrNull { config ->
      drmPreferences.indexOfFirst { it.isCompatibleWith(config) }
    } ?: Int.MAX_VALUE

  /**
   * Returns the first [DrmConfig] from this source that matches the client's [drmPreferences],
   * respecting preference order, or `null` if the source is unprotected.
   */
  private fun MediaSource.preferredDrm(): DrmConfig? =
    drmPreferences.firstNotNullOfOrNull { pref ->
      drmConfigs.find { pref.isCompatibleWith(it) }
    }
}

/**
 * Maps well-known DRM key-system / security-level pairs to a numeric rank
 * so that levels can be compared across different DRM schemes.
 *
 * A **lower rank** means a **stronger** security level (e.g. Widevine L1 = 1, L3 = 3).
 *
 * Currently supported key systems:
 * - `com.widevine.alpha`: L1, L2, L3
 * - `com.microsoft.playready`: SL3000, SL2000, SL150
 */
private object SecurityLevels {
  /** Maps `(keySystem, level)` pairs to their numeric rank. */
  private val rankings: Map<Pair<String, String>, Int> =
    mapOf(
      (DrmSystems.WIDEVINE to "L1") to 1,
      (DrmSystems.WIDEVINE to "L2") to 2,
      (DrmSystems.WIDEVINE to "L3") to 3,
      (DrmSystems.PLAYREADY to "SL3000") to 1,
      (DrmSystems.PLAYREADY to "SL2000") to 2,
      (DrmSystems.PLAYREADY to "SL150") to 3,
    )

  /**
   * Returns the numeric rank for the given [keySystem] and [level],
   * or `null` if the combination is unknown.
   *
   * @param keySystem The DRM key system identifier (e.g. `"com.widevine.alpha"`).
   * @param level The security level string (e.g. `"L1"`, `"SL3000"`).
   */
  fun rankOf(
    keySystem: String,
    level: String,
  ): Int? = rankings[keySystem to level]

  /**
   * Checks whether the [actual] security level of the client is strong enough
   * to satisfy the [required] level demanded by a DRM config.
   *
   * Comparison is rank-based: a lower-or-equal rank means the client meets or exceeds
   * the requirement. Returns `false` if either level is unknown for the given [keySystem].
   *
   * @param keySystem The DRM key system identifier.
   * @param actual The client's reported security level.
   * @param required The security level required by the DRM config.
   * @return `true` if the client's level is at least as strong as [required].
   */
  @SuppressWarnings("ReturnCount")
  fun isCompatible(
    keySystem: String,
    actual: String,
    required: String,
  ): Boolean {
    val actualRank = rankOf(keySystem, actual) ?: return false
    val requiredRank = rankOf(keySystem, required) ?: return false
    return actualRank <= requiredRank
  }
}
