import htmx from "htmx.org";
import { showSnackbar } from "../../../shared/components/snackbar.component.js";

/**
 * Opens the move-to-folder dialog once the picker content has finished loading.
 * @listens document#htmx:afterRequest
 */
document.addEventListener("htmx:afterRequest", function(e) {
  if (!e.target.hasAttribute("data-open-move-dialog") || !e.detail.successful) return;

  document.getElementById("move-folder-dialog").showModal();
});

/**
 * Toggles a folder row open or closed in the picker tree and updates its icon.
 * @listens document#click
 */
document.addEventListener("click", function(e) {
  const expandBtn = e.target.closest(".folder-expand");

  if (!expandBtn) return;

  const children = document.getElementById(expandBtn.dataset.childrenId);
  const isNowOpen = expandBtn.classList.toggle("is-open");
  const icon = expandBtn.querySelector(".material-symbols-outlined");

  if (icon) icon.textContent = isNowOpen ? "folder_open" : "folder";
  if (children) children.classList.toggle("is-open", isNowOpen);
});

/**
 * Marks the clicked folder row as selected and arms the move-confirm button
 * with the target folder's ID and name.
 * @listens document#click
 */
// eslint-disable-next-line max-statements
document.addEventListener("click", function(e) {
  const selectBtn = e.target.closest(".folder-select");

  if (!selectBtn) return;

  const dialog = document.getElementById("move-folder-dialog");

  if (!dialog) return;

  dialog.querySelectorAll(".folder-row.is-selected").forEach(r => r.classList.remove("is-selected"));
  selectBtn.closest(".folder-row").classList.add("is-selected");

  const confirmBtn = dialog.querySelector("[data-move-confirm]");

  if (confirmBtn) {
    confirmBtn.disabled = false;
    confirmBtn.dataset.folderId = selectBtn.dataset.folderId;
    confirmBtn.dataset.folderName = selectBtn.dataset.folderName;
  }
});

/**
 * Submits the folder assignment when the move-confirm button is clicked, then
 * closes the dialog and shows a snackbar. If the selected folder is already
 * the media's current folder the dialog is closed immediately without a request.
 * @listens document#click
 */
// eslint-disable-next-line complexity
document.addEventListener("click", function(e) {
  const confirmBtn = e.target.closest("[data-move-confirm]");

  if (!confirmBtn || confirmBtn.disabled) return;

  const dialog = document.getElementById("move-folder-dialog");
  const mediaId = dialog.querySelector("#picker-media-id")?.value;
  const currentFolderId = dialog.querySelector("#picker-current-folder-id")?.value;
  const { folderId, folderName } = confirmBtn.dataset;

  if (folderId === currentFolderId) {
    dialog.close();

    return;
  }

  htmx.ajax("POST", `/console/actions/folder/${folderId}/media`, {
    values: { mediaId },
    swap: "none",
  }).then(() => {
    dialog.close();
    showSnackbar(`Moved to "${folderName}"`, confirmBtn);
  });
});

/**
 * Closes the move-to-folder dialog and shows a confirmation snackbar after a
 * successful remove-from-folder request.
 * @listens document#htmx:afterRequest
 */
document.addEventListener("htmx:afterRequest", function(e) {
  if (!e.target.classList.contains("folder-picker-unassign") || !e.detail.successful) return;

  document.getElementById("move-folder-dialog")?.close();
  showSnackbar("Removed from folder", e.target);
});
