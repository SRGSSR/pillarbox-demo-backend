package ch.srgssr.pillarbox.backend.entrypoint.web.service

import ch.srgssr.pillarbox.backend.entrypoint.web.utils.DrmSystems

/**
 * A client's preference for a specific DRM key system, optionally
 * constrained to a maximum security level.
 *
 * @property keySystem The key system for this preference.
 * @property maxSecurityLevel The highest acceptable security level for this key system.
 *                            Defaults to the weakest known level of [keySystem],
 *                            or `null` for unknown key systems (unconstrained).
 */
data class DrmPreference(
  val keySystem: String,
  val maxSecurityLevel: String? = minSecurityLevels[keySystem],
)

/**
 * The weakest security level of each known key system, used as the default
 * [DrmPreference.maxSecurityLevel] when a client does not provide one.
 */
private val minSecurityLevels: Map<String, String> =
  mapOf(
    DrmSystems.WIDEVINE to "L3",
    DrmSystems.PLAYREADY to "SL2000",
  )

/**
 * Maps EME robustness levels to DRM-specific security levels per key system.
 */
private val robustnessToLevel: Map<Pair<String, String>, String> =
  mapOf(
    (DrmSystems.WIDEVINE to "SW_SECURE_CRYPTO") to "L3",
    (DrmSystems.WIDEVINE to "SW_SECURE_DECODE") to "L3",
    (DrmSystems.WIDEVINE to "HW_SECURE_CRYPTO") to "L2",
    (DrmSystems.WIDEVINE to "HW_SECURE_DECODE") to "L2",
    (DrmSystems.WIDEVINE to "HW_SECURE_ALL") to "L1",
    (DrmSystems.PLAYREADY to "SW_SECURE_CRYPTO") to "SL2000",
    (DrmSystems.PLAYREADY to "SW_SECURE_DECODE") to "SL2000",
    (DrmSystems.PLAYREADY to "HW_SECURE_CRYPTO") to "SL2000",
    (DrmSystems.PLAYREADY to "HW_SECURE_DECODE") to "SL2000",
    (DrmSystems.PLAYREADY to "HW_SECURE_ALL") to "SL3000",
  )

/**
 * Parses a DRM preference string of the form `"keySystem"` or `"keySystem;securityLevel"`.
 *
 * @receiver the raw preference string.
 * @return the parsed [DrmPreference].
 */
fun String.toDrmPreference(): DrmPreference {
  if (";" !in this) return DrmPreference(keySystem = this)

  val (keySystem, level) = split(";", limit = 2).map { it.trim() }

  return level
    .takeIf { it.isNotEmpty() }
    ?.let { DrmPreference(keySystem, robustnessToLevel[keySystem to it] ?: it) }
    ?: DrmPreference(keySystem)
}

/**
 * Parses a list of raw DRM preference strings.
 *
 * @receiver list of raw strings.
 * @return list of parsed [DrmPreference]s, preserving priority order.
 */
fun List<String>.toDrmPreferences(): List<DrmPreference> = mapNotNull { it.toDrmPreference() }
