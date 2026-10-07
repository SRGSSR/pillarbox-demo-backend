import "../../layouts/dashboard.layout.js";
import { showSnackbar } from "../../shared/components/snackbar.component.js";
import "./fragments/folder-grid.fragment.js";
import "./fragments/folder-picker.fragment.js";
import "./fragments/folder-permissions.fragment.js";
import "../../shared/fragments/media-grid.fragment.js";

/**
 * Copies the media player API URL or the media ID to the clipboard when a copy
 * button is clicked, then closes the popover menu hosting the button, if any.
 * @listens document#click
 */
document.addEventListener("click", function(e) {
  const button = e.target.closest("[data-copy-url], [data-copy-id]");

  if (!button) return;

  const isId = "copyId" in button.dataset;

  navigator.clipboard.writeText(
    isId ? button.dataset.copyId : `${window.location.origin}${button.dataset.copyUrl}`
  );

  showSnackbar(isId ? "Media Id Copied!" : "Link Copied!", button.closest(".media-card"));
  button.closest("[popover]")?.hidePopover();
});
