import { showSnackbar } from "../../shared/components/snackbar.component.js";
import "./fragments/folder-grid.fragment.js";
import "./fragments/folder-picker.fragment.js";
import "./fragments/folder-permissions.fragment.js";
import "../../shared/fragments/media-grid.fragment.js";

/**
 * Copies the media player API URL to the clipboard when a copy button is clicked.
 * @listens document#click
 */
document.addEventListener("click", function(e) {
  const button = e.target.closest("[data-copy-url]");

  if (!button) return;

  navigator.clipboard.writeText(
    `${window.location.origin}${button.dataset.copyUrl}`
  );

  showSnackbar("Link Copied!", button);
});

/**
 * Intercepts HTMX confirm prompts and shows a custom modal dialog instead of
 * the browser's native confirm().
 * @listens document#htmx:confirm
 */
document.addEventListener("htmx:confirm", function(e) {
  if (!e.detail.question) return;

  e.preventDefault();

  const template = document.getElementById("confirm-dialog-template");
  const clone = template.content.cloneNode(true);
  const dialog = clone.querySelector("dialog");

  dialog.querySelector("#confirm-message").textContent = e.detail.question;
  document.body.appendChild(dialog);

  dialog.addEventListener("close", () => {
    if (dialog.returnValue === "confirm") {
      e.detail.issueRequest(true);
    }
    dialog.remove();
  });

  dialog.showModal();
});
