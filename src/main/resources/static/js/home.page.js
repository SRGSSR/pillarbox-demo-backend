import "htmx.org";
import { showSnackbar } from "./components/snackbar.component.js";

/**
 * Copies the Player API URL of a media card to the clipboard when the copy
 * button is clicked.
 *
 * @listens document#click
 * @param {MouseEvent} e - The click event.
 */
document.addEventListener("click", function(e) {
  const button = e.target.closest("[data-copy-url]");

  if (!button) return;

  navigator.clipboard.writeText(
    window.location.origin + button.dataset.copyUrl
  );
  showSnackbar("Link Copied!", button);
});

/**
 * Listens for htmx confirm events and displays a native modal dialog
 * using a predefined template element instead of the default browser confirm().
 *
 * Expects a <template id="confirm-dialog-template"> in the DOM containing
 * a <dialog> with a #confirm-message element inside.
 *
 * @listens document#htmx:confirm
 * @param {CustomEvent} e - The htmx:confirm event.
 * @param {string} e.detail.question - The confirmation message to display.
 * @param {function(boolean): void} e.detail.issueRequest - Callback to proceed with the request.
 */
document.addEventListener("htmx:confirm", function(e) {
  if (!e.detail.question) return;

  e.preventDefault();

  const template = document.getElementById('confirm-dialog-template');
  const clone = template.content.cloneNode(true);
  const dialog = clone.querySelector('dialog');

  dialog.querySelector('#confirm-message').textContent = e.detail.question;
  document.body.appendChild(dialog);
  dialog.addEventListener('close', () => {
    if (dialog.returnValue === 'confirm') {
      e.detail.issueRequest(true);
    }
    dialog.remove();
  });

  dialog.showModal();
});
