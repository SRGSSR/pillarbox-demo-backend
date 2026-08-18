if (!globalThis.Temporal) await import('temporal-polyfill/global');

/**
 * Converts a datetime-local value, read in the given zone, into an ISO instant.
 * @param {string} value - The control's value, e.g. `2026-06-06T14:32`.
 * @param {string} timeZone - The IANA time zone the value is read in.
 * @returns {string|null} The ISO instant, or null when the value is not a date.
 */
export function toInstant(value, timeZone) {
  try {
    return globalThis.Temporal.PlainDateTime
      .from(value)
      .toZonedDateTime(timeZone)
      .toInstant()
      .toString();
  } catch (e) {
    console.error(e);

    return null;
  }
}
