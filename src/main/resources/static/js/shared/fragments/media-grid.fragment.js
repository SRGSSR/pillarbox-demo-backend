import { assignMedia, removeMedia } from "../media-actions.js";
import { selectedMedia } from "./media-selection.fragment.js";

// Folder cards and ancestor breadcrumbs receive the media. The root breadcrumb
// removes them from the current folder.
const DROP_TARGET = "[data-drop-folder], [data-drop-unassign]";

let draggedIds = [];

/**
 * Lists the media that a drag started on a card moves: the whole selection
 * when the card is selected, the card alone otherwise.
 * @param {HTMLElement} card - The card the drag started on.
 * @returns {string[]} The ids of the dragged media.
 */
function draggedMedia(card) {
  const isSelected = card.querySelector("[data-select-media]")?.checked;

  return isSelected
    ? selectedMedia().map(item => item.id)
    : [card.dataset.mediaId];
}

/**
 * Marks or unmarks the dragged cards as being dragged.
 * @param {boolean} dragging - Whether a drag is in progress.
 */
function setDragging(dragging) {
  draggedIds.forEach((id) => {
    document.getElementById(`media-card-${id}`)
      ?.classList.toggle("dragging", dragging);
  });
}

/**
 * Records the dragged media when the user starts dragging a media card.
 * @listens document#dragstart
 */
document.addEventListener("dragstart", function(e) {
  const card = e.target.closest(".media-card[draggable]");

  if (!card) return;

  draggedIds = draggedMedia(card);
  e.dataTransfer.effectAllowed = "move";
  e.dataTransfer.setData("text/plain", card.dataset.mediaId);
  setDragging(true);
});

/**
 * Cleans up the dragging state when the drag ends, regardless of drop target.
 * @listens document#dragend
 */
document.addEventListener("dragend", function() {
  setDragging(false);
  draggedIds = [];

  document.querySelectorAll(".drag-over").forEach((el) => {
    el.classList.remove("drag-over");
  });
});

/**
 * Highlights a drop target while media cards are dragged over it.
 * @listens document#dragover
 */
document.addEventListener("dragover", function(e) {
  const target = e.target.closest(DROP_TARGET);

  if (!target || draggedIds.length === 0) return;

  e.preventDefault();
  e.dataTransfer.dropEffect = "move";
  target.classList.add("drag-over");
});

/**
 * Removes the highlight when the cursor leaves a drop target.
 * @listens document#dragleave
 */
document.addEventListener("dragleave", function(e) {
  const target = e.target.closest(DROP_TARGET);

  if (target && !target.contains(e.relatedTarget)) {
    target.classList.remove("drag-over");
  }
});

/**
 * Moves the dragged media to the folder they are dropped on, or removes them
 * from the current folder when dropped on the root breadcrumb.
 * @listens document#drop
 */
document.addEventListener("drop", function(e) {
  const target = e.target.closest(DROP_TARGET);

  if (!target || draggedIds.length === 0) return;

  e.preventDefault();

  const { dropFolder, dropUnassign } = target.dataset;

  if (dropUnassign) removeMedia(draggedIds, dropUnassign);
  else assignMedia(draggedIds, dropFolder);
});
