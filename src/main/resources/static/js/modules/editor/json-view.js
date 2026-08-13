import { createJsonEditor } from "../../shared/components/json-editor.component.js";

const view = document.querySelector('.json-view');
const toggle = document.getElementById('view-toggle');
const form = document.querySelector('.media-form');
const dialog = document.getElementById('json-discard-dialog');
const editor = view ? createJsonEditor(view.querySelector('.json-editor')) : null;

/**
 * Returns whether the JSON view is currently shown, derived from the
 * toggle button's pressed state.
 * @returns {boolean} True while the JSON view is active.
 */
function jsonViewActive() {
  return toggle.getAttribute('aria-pressed') === 'true';
}

/**
 * Returns whether a tab panel belongs to the currently selected tab.
 * @param {HTMLElement} panel - The tab panel element.
 * @returns {boolean} True when the panel's tab is selected.
 */
function isSelectedPanel(panel) {
  const tab = document.getElementById(panel.getAttribute('aria-labelledby'));

  return tab?.getAttribute('aria-selected') === 'true';
}

/**
 * Reflects the toggle state into the page: shows the JSON view or the
 * form view with its selected tab panel, and relabels the toggle button.
 */
function syncView() {
  const active = jsonViewActive();
  const icon = toggle.querySelector('.material-symbols-outlined');
  const label = toggle.querySelector('.view-toggle-label');

  document.querySelector('.editor-tabs').hidden = active;
  view.hidden = !active;
  document.querySelectorAll('.tab-panel').forEach(panel => {
    panel.hidden = active || !isSelectedPanel(panel);
  });
  icon.textContent = active ? 'edit_note' : 'data_object';
  label.textContent = active ? 'Form' : 'JSON';
  form.noValidate = active;
}

/**
 * Asks whether to discard unsaved JSON edits.
 * @returns {Promise<boolean>} Resolves true when discarding is confirmed.
 */
function confirmDiscard() {
  dialog.returnValue = '';

  return new Promise(resolve => {
    dialog.addEventListener('close', () => {
      resolve(dialog.returnValue === 'discard');
    }, {once: true});
    dialog.showModal();
  });
}

/**
 * Opens the JSON view over the serialized form state.
 * @param {function(): string} serialize - Serializes the form to JSON text.
 */
function enterJsonView(serialize) {
  editor.setText(serialize());
  toggle.setAttribute('aria-pressed', 'true');
  syncView();
}

/**
 * Returns to the form view. Since JSON edits are never applied to the
 * form, asks for confirmation first when the JSON text was changed.
 * @param {function(): string} serialize - Serializes the form to JSON text.
 */
async function leaveJsonView(serialize) {
  if (editor.getText() !== serialize() && !(await confirmDiscard())) return;

  toggle.setAttribute('aria-pressed', 'false');
  syncView();
}

/**
 * Wires the JSON view: the toggle switches views, and while the JSON view
 * is active the editor text is the payload submitted by the form.
 * @param {function(): string} serialize - Serializes the form to JSON text.
 * @returns {{active: function(): boolean, text: function(): string}|null}
 *   Accessors for the submit path, or null when the page has no JSON view.
 */
export function initJsonView(serialize) {
  if (!editor || !toggle) return null;

  /**
   * @listens HTMLButtonElement#click
   */
  toggle.addEventListener('click', () => {
    if (jsonViewActive()) {
      leaveJsonView(serialize);
    } else {
      enterJsonView(serialize);
    }
  });

  /**
   * Blocks saving from the JSON view while the text is not valid JSON.
   * @listens document#htmx:beforeRequest
   */
  document.addEventListener('htmx:beforeRequest', (evt) => {
    if (jsonViewActive() && evt.detail.elt === form && !editor.read().valid) {
      evt.preventDefault();
    }
  });

  return {active: jsonViewActive, text: editor.getText};
}
