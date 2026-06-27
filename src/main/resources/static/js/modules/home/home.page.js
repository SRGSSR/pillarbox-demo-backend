import "../../layouts/dashboard.layout.js";
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
