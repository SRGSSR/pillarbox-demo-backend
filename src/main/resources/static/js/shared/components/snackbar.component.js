const snackbarComponent = document.getElementById("snackbar");
let snackbarTimeout = null;

/**
 * Displays a transient snackbar message anchored above a given element.
 *
 * The snackbar is shown immediately and hidden automatically after 2 seconds.
 * Calling this function while the snackbar is already visible resets the timer
 * and repositions it above the new anchor.
 *
 * @param {string} message - The text to display in the snackbar.
 * @param {Element} anchor - The element above which the snackbar is positioned.
 */
export function showSnackbar(message, anchor) {
  const rect = anchor.getBoundingClientRect();

  snackbarComponent.textContent = message;
  snackbarComponent.style.left = `${rect.left + rect.width / 2}px`;
  snackbarComponent.style.top = `${rect.top}px`;

  clearTimeout(snackbarTimeout);
  if (!snackbarComponent.matches(":popover-open")) {
    snackbarComponent.showPopover();
  }
  snackbarTimeout = setTimeout(() => snackbarComponent.hidePopover(), 2000);
}
