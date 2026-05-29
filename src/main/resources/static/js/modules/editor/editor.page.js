import htmx from 'htmx.org';
import dot from 'dot-object';

/**
 * Maps input[type] values to their corresponding coercion functions.
 * @type {Record<string, function(HTMLInputElement): *>}
 */
const TYPE_COERCERS = {
  number: (input) => Number(input.value),
  checkbox: (input) => input.checked,
};

/**
 * Maps data-type attribute values to their corresponding coercion functions.
 * @type {Record<string, function(string): *>}
 */
const DATA_TYPE_COERCERS = {
  json: (value) => parseJsonValue(value),
  array: (value) => value.split(',').map(v => v.trim()),
};

/**
 * Coerces an input element's value to the appropriate type based on its
 * type attribute or data-type attribute.
 * @param {HTMLInputElement} input - The input element to coerce.
 * @returns {*} The coerced value.
 */
function coerceValue(input) {
  if (input.hasAttribute('data-number')) return Number(input.value);

  const typeCoercer = TYPE_COERCERS[input.type];

  if (typeCoercer) return typeCoercer(input);

  const dataCoercer = DATA_TYPE_COERCERS[input.getAttribute('data-type')];

  return dataCoercer ? dataCoercer(input.value) : input.value;
}

/**
 * Safely parses a JSON string, returning null if parsing fails.
 * @param {string} value - The JSON string to parse.
 * @returns {*|null} The parsed value, or null if parsing fails.
 */
function parseJsonValue(value) {
  try {
    return JSON.parse(value);
  } catch (e) {
    console.error(e);

    return null;
  }
}

/**
 * Collects and coerces all named input values within an element into a
 * flat params object, skipping empty or null values.
 * @param {HTMLElement} elt - The container element to query inputs from.
 * @returns {Record<string, *>} A flat object of coerced input values keyed by name.
 */
function collectParams(elt) {
  const params = {};

  elt.querySelectorAll('[name]').forEach(input => {
    if (input.value === '' || input.value === null) return;

    const value = coerceValue(input);

    if (value === null) return;
    params[input.getAttribute('name')] = value;
  });

  return params;
}

/**
 * JSON.stringify replacer that removes null and undefined items from arrays.
 * @param {string} key - The current key being serialized.
 * @param {*} value - The current value being serialized.
 * @returns {*} The original value, or a filtered array with nulls removed.
 */
function stripNullsFromArrays(key, value) {
  if (!Array.isArray(value)) return value;

  return value.filter(item => item !== null && item !== undefined);
}

/**
 * htmx extension that encodes form parameters as a dot-notation JSON body.
 * Input values are coerced to their appropriate types based on input[type]
 * or data-type attributes before being serialized.
 * @see https://htmx.org/api/#defineExtension
 */
htmx.defineExtension('dot-json', {
  /**
   * @param {XMLHttpRequest} xhr - The XMLHttpRequest instance.
   * @param {Object} parameters - The default htmx parameters (unused, replaced by DOM collection).
   * @param {HTMLElement} elt - The element that triggered the request.
   * @returns {string} A JSON string of the coerced and dot-expanded parameters.
   */
  encodeParameters(xhr, parameters, elt) {
    xhr.setRequestHeader('Content-Type', 'application/json');

    return JSON.stringify(dot.object(collectParams(elt)), stripNullsFromArrays);
  }
});

/**
 * Listens for htmx config requests and injects a computed index parameter
 * based on a selector defined in the triggering element's dataset.
 * @listens document#htmx:configRequest
 */
/**
 * Resolves the next insertion index from the element indicated by a CSS selector.
 * Falls back to 0 if the selector matches nothing or is invalid.
 * @param {string} selector - CSS selector pointing to the last existing entry item.
 * @returns {number} The next index to use.
 */
function resolveNextIndex(selector) {
  try {
    const index = document.querySelector(selector)?.dataset?.index;

    return !index ? 0 : parseInt(index) + 1;
  } catch {
    return 0;
  }
}

/**
 * Listens for htmx config requests and injects a computed index parameter
 * based on a selector defined in the triggering element's dataset.
 * @listens document#htmx:configRequest
 */
document.addEventListener('htmx:configRequest', (evt) => {
  const { indexSelector: selector } = evt.detail.elt.dataset;

  if (!selector) return;

  evt.detail.parameters['index'] = resolveNextIndex(selector);
});

/**
 * Updates the badge inside a tab button to reflect the current item count.
 * Hides the badge when the count is zero.
 * @param {HTMLButtonElement} btn - The tab button containing the badge.
 * @param {number} count - The current item count.
 */
function syncBadge(btn, count) {
  const badge = btn.querySelector('.tab-badge');

  if (!badge) return;

  badge.textContent = count;
  badge.hidden = !count;
}

/**
 * Re-counts entry items in a tab's list and syncs its badge and the numeric
 * part of the panel count label. Skips tabs that have no entry list.
 * @param {HTMLButtonElement} btn - The tab button to update.
 */
function updateTabCount(btn) {
  const panel = document.getElementById(btn.getAttribute('aria-controls'));
  const list = panel?.querySelector('.entry-list');

  if (!list) return;

  const count = list.querySelectorAll(':scope > .entry-item').length;

  syncBadge(btn, count);

  const countValue = panel.querySelector('.count-value');

  if (countValue) countValue.textContent = count;
}

/**
 * Re-counts entry items across all tabs.
 */
function updateTabCounts() {
  tabButtons.forEach(updateTabCount);
}

/**
 * Returns true when a mutation added or removed an entry item node.
 * @param {MutationRecord} m - The mutation record to inspect.
 * @returns {boolean}
 */
function isEntryItemMutation(m) {
  return [...m.addedNodes, ...m.removedNodes].some(n => n.classList?.contains('entry-item'));
}

/**
 * All tab trigger buttons in the editor navigation.
 * @type {HTMLButtonElement[]}
 */
const tabButtons = Array.from(document.querySelectorAll('.editor-tab[data-tab]'));

/**
 * Activates a tab by setting aria-selected on all tab buttons and toggling
 * visibility of their associated panels via the hidden attribute.
 * @param {HTMLButtonElement} btn - The tab button to activate.
 */
function activateTab(btn) {
  tabButtons.forEach(b => {
    const isActive = b === btn;

    b.setAttribute('aria-selected', String(isActive));

    const panel = document.getElementById(b.getAttribute('aria-controls'));

    if (panel) panel.hidden = !isActive;
  });
}

/**
 * @listens HTMLButtonElement#click
 * @listens HTMLButtonElement#keydown
 */
tabButtons.forEach((btn, i) => {
  btn.addEventListener('click', () => activateTab(btn));
  btn.addEventListener('keydown', (e) => {
    const isNext = ['ArrowDown', 'ArrowRight'].includes(e.key);
    const isPrev = ['ArrowUp', 'ArrowLeft'].includes(e.key);

    if (!isNext && !isPrev) return;

    e.preventDefault();

    const tabIndex =
      (i + (isNext ? 1 : -1) + tabButtons.length) % tabButtons.length;
    const target = tabButtons[tabIndex];

    target.focus();
    activateTab(target);
  });
});

/**
 * @listens document#htmx:afterSettle
 */
document.addEventListener('htmx:afterSettle', updateTabCounts);

const form = document.querySelector('.media-form');

/**
 * Toggles the `tab-in-error` class on a tab button based on whether its
 * panel contains invalid fields after a submission attempt.
 * @param {HTMLButtonElement} btn - The tab button to update.
 */
function updateTabErrorDot(btn) {
  const panel = document.getElementById(btn.getAttribute('aria-controls'));
  const hasErrors = form?.hasAttribute('data-submitted') && panel?.querySelector(':invalid') != null;

  btn.classList.toggle('tab-in-error', hasErrors);
  btn.title = hasErrors ? 'This tab contains errors' : '';
}

/**
 * Refreshes error indicators across all tab buttons.
 */
function syncTabErrors() {
  tabButtons.forEach(updateTabErrorDot);
}

/**
 * Switches to the first tab panel that contains an invalid field, so the
 * browser's native validation tooltip renders on a visible element.
 */
function activateFirstErrorTab() {
  const btn = tabButtons.find(b => {
    const panel = document.getElementById(b.getAttribute('aria-controls'));

    return panel?.querySelector(':invalid') != null;
  });

  if (btn) activateTab(btn);
}

if (form) {
  new MutationObserver((mutations) => {
    if (mutations.some(isEntryItemMutation)) updateTabCounts();
  }).observe(form, {childList: true, subtree: true});

  /**
   * Fires on each invalid control during native constraint validation.
   * Marks the form as submitted, refreshes error indicators and switches
   * to the first tab containing an error so the browser tooltip is visible.
   * @listens HTMLFormElement#invalid
   */
  form.addEventListener('invalid', () => {
    form.setAttribute('data-submitted', '');
    syncTabErrors();
    activateFirstErrorTab();
  }, true);

  /**
   * Refreshes tab error indicators as the user corrects fields after a
   * failed submission attempt.
   * @listens HTMLFormElement#input
   */
  form.addEventListener('input', () => {
    if (form.hasAttribute('data-submitted')) syncTabErrors();
  });
}
