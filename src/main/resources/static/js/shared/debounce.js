/**
 * Returns a wrapper that delays calls to fn until the given quiet period
 * has elapsed since the last invocation.
 * @param {Function} fn - The function to debounce.
 * @param {number} delayMs - The quiet period in milliseconds.
 * @returns {Function} The debounced wrapper.
 */
export function debounce(fn, delayMs) {
  let timeout = null;

  return (...args) => {
    clearTimeout(timeout);
    timeout = setTimeout(() => fn(...args), delayMs);
  };
}
