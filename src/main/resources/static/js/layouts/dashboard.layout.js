import "htmx.org";

/**
 * Mirrors a popover dropdown's open state onto its invoker's aria-expanded,
 * since popovers light-dismiss without notifying the triggering button.
 * @listens document#toggle
 */
document.addEventListener("toggle", (e) => {
  const menu = e.target;

  if (!(menu instanceof HTMLElement) || !menu.matches(".dropdown")) return;

  const invoker = document.querySelector(`[popovertarget="${CSS.escape(menu.id)}"]`);

  if (!invoker) return;

  invoker.setAttribute("aria-expanded", String(e.newState === "open"));
}, true);

// Backdrop clicks close dialogs in browsers without closedby support.
if (!("closedBy" in HTMLDialogElement.prototype)) {
  /**
   * Closes a modal dialog when its backdrop (the dialog element itself) is clicked.
   * @listens document#click
   */
  document.addEventListener("click", (e) => {
    if (e.target instanceof HTMLDialogElement && e.target.open) {
      e.target.close();
    }
  });
}

/**
 * Fills a slot with text, or drops it when there's nothing to show.
 * @param {DocumentFragment|Element} root - Element to search within.
 * @param {string} selector - Slot selector.
 * @param {string} [text] - Text to render, or empty to remove the slot.
 */
function fillSlot(root, selector, text) {
  const el = root.querySelector(selector);

  if (text) el.textContent = text;
  else el.remove();
}

/**
 * Labels the action buttons and applies destructive styling to a confirm dialog.
 * @param {HTMLDialogElement} dialog - The dialog to style.
 * @param {DOMStringMap} cfg - The trigger's data-confirm-* dataset.
 */
function styleConfirmActions(dialog, cfg) {
  const confirmBtn = dialog.querySelector(".confirm-action");
  const cancelBtn = dialog.querySelector(".confirm-cancel");

  confirmBtn.textContent = cfg.confirmAction || "Confirm";
  cancelBtn.textContent = cfg.confirmCancel || "Cancel";

  const danger = "confirmDanger" in cfg;

  dialog.toggleAttribute("data-danger", danger);
  confirmBtn.classList.toggle("btn-primary", !danger);
  confirmBtn.classList.toggle("btn-danger", danger);
}

/**
 * Builds a confirm dialog from the template for the given prompt. hx-confirm is
 * the question; an optional description, icon, action label and destructive
 * styling come from data-confirm-* hooks on the trigger.
 * @param {string} question - The hx-confirm question, shown as the title.
 * @param {DOMStringMap} cfg - The trigger's data-confirm-* dataset.
 * @returns {HTMLDialogElement} The populated, detached dialog.
 */
function buildConfirmDialog(question, cfg) {
  const template = document.getElementById("confirm-dialog-template");
  const dialog = template.content.cloneNode(true).querySelector("dialog");

  dialog.querySelector(".confirm-title").textContent = question;
  fillSlot(dialog, ".confirm-icon", cfg.confirmIcon);
  fillSlot(dialog, ".confirm-description", cfg.confirmDescription);
  if (!cfg.confirmDescription) dialog.removeAttribute("aria-describedby");

  styleConfirmActions(dialog, cfg);

  return dialog;
}

/**
 * Replaces htmx's native confirm() with a themed modal. Only one confirm dialog
 * is ever live, so the template's static ids are safe.
 * @listens document#htmx:confirm
 */
document.addEventListener("htmx:confirm", (e) => {
  if (!e.detail.question) return;

  e.preventDefault();

  const dialog = buildConfirmDialog(e.detail.question, e.detail.elt.dataset);

  dialog.addEventListener("close", () => {
    if (dialog.returnValue === "confirm") e.detail.issueRequest(true);
    dialog.remove();
  }, { once: true });

  document.body.appendChild(dialog);
  dialog.showModal();

  // Park focus on the safe action for destructive prompts.
  if (dialog.hasAttribute("data-danger")) {
    dialog.querySelector(".confirm-cancel").focus();
  }
});

/**
 * Resolves the active theme from the document, falling back to the OS setting.
 * @returns {string} Either "dark" or "light".
 */
function currentTheme() {
  return document.documentElement.dataset.theme ||
    (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
}

/**
 * Reflects the active theme onto every theme switch control.
 * @param {string} theme - The active theme.
 */
function syncThemeToggles(theme) {
  document.querySelectorAll(".theme-toggle").forEach((btn) => {
    btn.setAttribute("aria-checked", theme === "dark");
  });
}

/**
 * Flips and persists the theme when a theme switch control is clicked.
 * @listens document#click
 */
document.addEventListener("click", (e) => {
  if (!e.target.closest(".theme-toggle")) return;

  const next = currentTheme() === "dark" ? "light" : "dark";

  document.documentElement.dataset.theme = next;
  try { localStorage.setItem("theme", next); } catch { /* storage unavailable */ }
  syncThemeToggles(next);
});

syncThemeToggles(currentTheme());
