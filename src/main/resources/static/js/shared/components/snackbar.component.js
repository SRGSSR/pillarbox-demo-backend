const snackbarComponent = document.getElementById("snackbar");
let snackbarTimeout = null;

/**
 * Positions the snackbar above the anchor's top centre, or pins it to the top
 * centre of the viewport when there is no anchor.
 *
 * @param {Element|null} anchor - The element to position above, or `null`.
 */
function positionSnackbar(anchor) {
  const rect = anchor?.getBoundingClientRect();

  snackbarComponent.classList.toggle("is-pinned", !rect);
  snackbarComponent.style.left = rect ? `${rect.left + rect.width / 2}px` : "";
  snackbarComponent.style.top = rect ? `${rect.top}px` : "";
}

/**
 * Displays a transient snackbar message.
 *
 * With an anchor the snackbar is positioned above that element; without one it
 * is pinned to the top centre of the viewport. It hides automatically after
 * 2 seconds; calling again while visible resets the timer and repositions it.
 *
 * @param {string} message - The text to display in the snackbar.
 * @param {Element} [anchor] - The element above which the snackbar is positioned.
 */
export function showSnackbar(message, anchor = null) {
  snackbarComponent.textContent = message;
  positionSnackbar(anchor);

  clearTimeout(snackbarTimeout);
  if (!snackbarComponent.matches(":popover-open")) {
    snackbarComponent.showPopover();
  }
  snackbarTimeout = setTimeout(() => snackbarComponent.hidePopover(), 2000);
}
