/**
 * Safely parses a JSON string, returning null if parsing fails.
 * @param {string} value - The JSON string to parse.
 * @returns {*|null} The parsed value, or null if parsing fails.
 */
export function parseJsonValue(value) {
  try {
    return JSON.parse(value);
  } catch (e) {
    console.error(e);

    return null;
  }
}
