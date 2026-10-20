import htmx from "htmx.org";
import { fillItemSummary } from "../../../shared/components/item-summary.component.js";
import { showSnackbar } from "../../../shared/components/snackbar.component.js";
import { selectedMedia } from "../../../shared/fragments/media-selection.fragment.js";
import { assignMedia } from "../../../shared/media-actions.js";

/**
 * Opens the move-to-folder dialog once the picker content has finished loading.
 * A picker without a media id moves the selected media, so it lists them.
 * @listens document#htmx:afterRequest
 */
document.addEventListener("htmx:afterRequest", function(e) {
  if (!e.target.hasAttribute("data-open-move-dialog") || !e.detail.successful) return;

  const dialog = document.getElementById("move-folder-dialog");
  const movesSelection = !dialog.querySelector("#picker-media-id").value;
  const titles = movesSelection ? selectedMedia().map(item => item.title) : [];

  fillItemSummary(dialog.querySelector(".item-summary"), titles);
  dialog.showModal();
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
 * Moves the media of the picker to a folder. A picker opened for one media
 * moves that media and shows a snackbar. A picker without a media id moves the
 * selected media in one request, whose response fills the snackbar.
 * @param {string} mediaId - The id of the single media, or empty for the selection.
 * @param {string} folderId - The id of the target folder.
 * @param {string} folderName - The name of the target folder.
 * @returns {Promise<void>} Resolves once the request has completed.
 */
function moveToFolder(mediaId, folderId, folderName) {
  if (!mediaId) {
    return assignMedia(selectedMedia().map(item => item.id), folderId);
  }

  return htmx.ajax("PUT", `/console/actions/folder/${folderId}/media/${encodeURIComponent(mediaId)}`, {
    swap: "none",
  }).then(() => showSnackbar(`Moved to "${folderName}"`));
}

/**
 * Submits the folder assignment when the move-confirm button is clicked, then
 * closes the dialog. If the selected folder is already the single media's
 * current folder the dialog is closed immediately without a request. A
 * selection always moves, because search results span several folders.
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

  if (mediaId && folderId === currentFolderId) {
    dialog.close();

    return;
  }

  moveToFolder(mediaId, folderId, folderName).then(() => dialog.close());
});

/**
 * Closes the move-to-folder dialog and shows a confirmation snackbar after a
 * successful remove-from-folder request. The batch response fills the snackbar
 * itself.
 * @listens document#htmx:afterRequest
 */
document.addEventListener("htmx:afterRequest", function(e) {
  if (!e.target.classList.contains("folder-picker-unassign") || !e.detail.successful) return;

  const dialog = e.target.closest("dialog");

  if (dialog.querySelector("#picker-media-id").value) showSnackbar("Removed from folder");
  dialog.close();
});
