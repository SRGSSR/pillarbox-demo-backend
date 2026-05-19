import htmx from "htmx.org";
import { showSnackbar } from "../components/snackbar.component.js";

/**
 * Sets drag data when the user starts dragging a media card.
 * @listens document#dragstart
 */
document.addEventListener("dragstart", function(e) {
  const card = e.target.closest(".media-card[draggable]");

  if (!card) return;

  e.dataTransfer.effectAllowed = "move";
  e.dataTransfer.setData("text/plain", card.dataset.mediaId);
  card.classList.add("dragging");
});

/**
 * Cleans up the dragging state when the drag ends, regardless of drop target.
 * @listens document#dragend
 */
document.addEventListener("dragend", function(e) {
  const card = e.target.closest(".media-card");

  if (card) card.classList.remove("dragging");

  document.querySelectorAll(".folder-card.drag-over").forEach(el => {
    el.classList.remove("drag-over");
  });
});

/**
 * Highlights a folder card while a media card is dragged over it.
 * @listens document#dragover
 */
document.addEventListener("dragover", function(e) {
  const folderCard = e.target.closest(".folder-card");

  if (!folderCard) return;

  e.preventDefault();
  e.dataTransfer.dropEffect = "move";
  folderCard.classList.add("drag-over");
});

/**
 * Removes the drag-over highlight when the cursor leaves a folder card boundary.
 * @listens document#dragleave
 */
document.addEventListener("dragleave", function(e) {
  const folderCard = e.target.closest(".folder-card");

  if (folderCard && !folderCard.contains(e.relatedTarget)) {
    folderCard.classList.remove("drag-over");
  }
});

/**
 * Posts the folder assignment via HTMX on drop and shows a confirmation snackbar.
 * @listens document#drop
 */
document.addEventListener("drop", function(e) {
  const folderCard = e.target.closest(".folder-card");

  if (!folderCard) return;

  e.preventDefault();
  folderCard.classList.remove("drag-over");

  const mediaId = e.dataTransfer.getData("text/plain");
  const folderId = folderCard.id.replace("folder-card-", "");
  const folderName = folderCard.querySelector(".folder-card-name")?.textContent?.trim() ?? "";

  htmx.ajax("POST", `/console/actions/folder/${folderId}/media`, {
    values: { mediaId },
    target: `[id='media-card-${mediaId}']`,
    swap: "delete",
  }).then(() => {
    const anchor = document.getElementById(folderCard.id) ?? folderCard;

    showSnackbar(`Moved to "${folderName}"`, anchor);
  });
});
