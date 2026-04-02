package ch.srgssr.pillarbox.backend.entrypoint.web.service

/**
 * A client's preference for a specific DRM key system, optionally
 * constrained to a maximum security level.
 *
 * @property keySystem The  key system for this preference.
 * @property maxSecurityLevel The highest acceptable security level for this key system,
 *                            or `null` if unconstrained.
 */
data class DrmPreference(
  val keySystem: String,
  val maxSecurityLevel: String? = null,
)

/**
 * Maps EME robustness levels to DRM-specific security levels per key system.
 */
private val robustnessToLevel: Map<Pair<String, String>, String> =
  mapOf(
    ("com.widevine.alpha" to "SW_SECURE_CRYPTO") to "L3",
    ("com.widevine.alpha" to "SW_SECURE_DECODE") to "L3",
    ("com.widevine.alpha" to "HW_SECURE_CRYPTO") to "L2",
    ("com.widevine.alpha" to "HW_SECURE_DECODE") to "L2",
    ("com.widevine.alpha" to "HW_SECURE_ALL") to "L1",
    ("com.microsoft.playready" to "SW_SECURE_CRYPTO") to "SL2000",
    ("com.microsoft.playready" to "SW_SECURE_DECODE") to "SL2000",
    ("com.microsoft.playready" to "HW_SECURE_CRYPTO") to "SL2000",
    ("com.microsoft.playready" to "HW_SECURE_DECODE") to "SL2000",
    ("com.microsoft.playready" to "HW_SECURE_ALL") to "SL3000",
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

  return DrmPreference(
    keySystem,
    level.takeIf { isNotEmpty() }?.let { robustnessToLevel[keySystem to level] ?: level },
  )
}

/**
 * Parses a list of raw DRM preference strings.
 *
 * @receiver list of raw strings.
 * @return list of parsed [DrmPreference]s, preserving priority order.
 */
fun List<String>.toDrmPreferences(): List<DrmPreference> = mapNotNull { it.toDrmPreference() }
