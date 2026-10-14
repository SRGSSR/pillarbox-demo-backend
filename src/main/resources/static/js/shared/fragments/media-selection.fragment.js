const bar = document.getElementById("selection-bar");
let lastToggled = null;

/**
 * Lists the selection checkboxes of the loaded media cards.
 * @returns {HTMLInputElement[]} The checkboxes, in document order.
 */
function checkboxes() {
  return [...document.querySelectorAll("[data-select-media]")];
}

/**
 * Lists the selected media.
 * @returns {{id: string, title: string}[]} The id and title of each selected media.
 */
export function selectedMedia() {
  return checkboxes()
    .filter(box => box.checked)
    .map(box => ({ id: box.value, title: box.dataset.title }));
}

/**
 * Shows the selection bar with the number of selected media, or hides it when
 * nothing is selected.
 */
function syncSelection() {
  if (!bar) return;

  const count = selectedMedia().length;

  document.body.classList.toggle("is-selecting", count > 0);
  bar.hidden = count === 0;
  bar.querySelector(".selection-count").textContent = `${count} selected`;
}

/**
 * Checks or unchecks every loaded media card.
 * @param {boolean} checked - The state to apply.
 */
function setAll(checked) {
  checkboxes().forEach(box => { box.checked = checked; });
  syncSelection();
}

/**
 * Applies the state of a checkbox to every checkbox between it and another one.
 * @param {HTMLInputElement} from - The checkbox toggled before.
 * @param {HTMLInputElement} to - The checkbox toggled now, which gives the state.
 */
function setRange(from, to) {
  const boxes = checkboxes();
  const start = boxes.indexOf(from);
  const end = boxes.indexOf(to);

  if (start < 0) return;

  boxes
    .slice(Math.min(start, end), Math.max(start, end) + 1)
    .forEach(box => { box.checked = to.checked; });
}

/**
 * Updates the selection when a checkbox is toggled. A shift-click also toggles
 * the cards between this checkbox and the previous one.
 * @listens document#click
 */
document.addEventListener("click", function(e) {
  const box = e.target.closest("[data-select-media]");

  if (!box) return;

  if (e.shiftKey) setRange(lastToggled, box);

  lastToggled = box;
  syncSelection();
});

/**
 * Toggles a card instead of opening the editor when it is clicked while
 * selecting. The click is forwarded to the card's checkbox.
 * @listens document#click
 */
document.addEventListener("click", function(e) {
  if (!document.body.classList.contains("is-selecting")) return;
  if (e.target.closest(".media-card-select, button, [popover]")) return;

  const box = e.target.closest(".media-card")?.querySelector("[data-select-media]");

  if (!box) return;

  e.preventDefault();
  box.dispatchEvent(new MouseEvent("click", { bubbles: true, shiftKey: e.shiftKey }));
});

/**
 * Handles the "Clear selection" and "Select all" buttons of the selection bar.
 * @listens HTMLElement#click
 */
bar?.addEventListener("click", function(e) {
  const action = e.target.closest("[data-selection-action]")?.dataset.selectionAction;

  if (action === "clear") setAll(false);
  if (action === "all") setAll(true);
});

/**
 * Clears the selection on Escape, unless a dialog or a menu is open.
 * @listens document#keydown
 */
document.addEventListener("keydown", function(e) {
  if (e.key !== "Escape" || !bar || bar.hidden) return;
  if (document.querySelector("dialog[open], .dropdown:popover-open")) return;

  setAll(false);
});

/**
 * Updates the selection bar after cards are loaded or removed.
 * @listens document#htmx:afterSettle
 */
document.addEventListener("htmx:afterSettle", syncSelection);
