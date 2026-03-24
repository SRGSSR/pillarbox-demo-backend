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
document.addEventListener('htmx:configRequest', (evt) => {
  const { indexSelector: selector } = evt.detail.elt.dataset;

  if (!selector) return;

  const index = document.querySelector(selector)?.dataset?.index;

  evt.detail.parameters['index'] = !index ? 0 : parseInt(index) + 1;
});
