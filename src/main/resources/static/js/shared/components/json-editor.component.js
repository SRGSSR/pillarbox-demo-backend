import { CodeJar } from 'codejar';
import Prism from 'prismjs/components/prism-core';
import 'prismjs/components/prism-json';
import { debounce } from '../debounce.js';

Prism.manual = true;

/**
 * Renders Prism JSON highlighting into the editor element.
 *
 * @param {HTMLElement} editor - The element whose text content is highlighted.
 */
function highlightJson(editor) {
  const code = editor.textContent;

  editor.innerHTML = Prism.highlight(code, Prism.languages.json, 'json');
}

/**
 * Pretty-prints JSON text, returning it unchanged when it does not parse.
 *
 * @param {string} text - The raw JSON text.
 *
 * @returns {string} The formatted text.
 */
function prettyText(text) {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

/**
 * Shows a message in the error strip and marks the code surface invalid.
 *
 * @param {HTMLElement} code - The code surface element.
 * @param {HTMLElement} error - The error strip element.
 * @param {string} message - The message to display.
 */
function renderError(code, error, message) {
  error.textContent = message;
  error.hidden = false;
  code.setAttribute('aria-invalid', 'true');
}

/**
 * Clears the error strip and the invalid mark on the code surface.
 *
 * @param {HTMLElement} code - The code surface element.
 * @param {HTMLElement} error - The error strip element.
 */
function clearError(code, error) {
  error.textContent = '';
  error.hidden = true;
  code.removeAttribute('aria-invalid');
}

/**
 * Parses editor text as JSON, reflecting the outcome in the error strip.
 *
 * @param {string} text - The raw editor text.
 * @param {HTMLElement} code - The code surface element.
 * @param {HTMLElement} error - The error strip element.
 * @param {boolean} allowEmpty - When true, blank text is valid with no value.
 *
 * @returns {{valid: boolean, value: (*|undefined)}} The parse outcome.
 */
function parseText(text, code, error, allowEmpty) {
  if (allowEmpty && text.trim() === '') {
    clearError(code, error);

    return {valid: true, value: undefined};
  }

  try {
    const value = JSON.parse(text);

    clearError(code, error);

    return {valid: true, value};
  } catch (e) {
    renderError(code, error, e.message);

    return {valid: false};
  }
}

/**
 * Creates the editor's code surface, named after the nearest `.field`
 * label when present.
 *
 * @param {HTMLElement} element - The element hosting the editor.
 *
 * @returns {HTMLElement} The code surface element.
 */
function createCodeSurface(element) {
  const code = document.createElement('pre');
  const label = element.closest('.field')?.querySelector('label');

  code.className = 'json-editor-code';
  code.setAttribute('aria-label', label?.textContent.trim() || 'JSON editor');
  code.spellcheck = false;

  return code;
}

/**
 * Creates the editor's error strip.
 *
 * @returns {HTMLElement} The error strip element.
 */
function createErrorStrip() {
  const error = document.createElement('p');

  error.className = 'json-editor-error';
  error.setAttribute('role', 'alert');
  error.hidden = true;

  return error;
}

/**
 * Creates the hidden form input mirroring the editor content.
 *
 * @param {string|null} name - The input name, or null for no form binding.
 *
 * @returns {HTMLInputElement|null} The created input, or null.
 */
function createInput(name) {
  if (!name) return null;

  const input = document.createElement('input');

  input.type = 'text';
  input.hidden = true;
  input.id = name;
  input.name = name;
  input.setAttribute('data-type', 'json');

  return input;
}

/**
 * Creates a syntax-highlighted JSON editor inside the element, seeded
 * from its text content. The code surface takes its accessible name from
 * the nearest `.field` label, falling back to a generic one.
 *
 * @param {HTMLElement} element - The element to build the editor in.
 * @param {string} [name] - Form input name; when set, the editor creates
 *   and syncs a hidden input carrying its content.
 * @param {number} [revalidationDebounce] - Quiet period in ms before
 *   re-validating edits.
 *
 * @returns {{setText: Function, getText: Function, read: Function}} The editor API.
 */
export function createJsonEditor(
  element,
  name = null,
  revalidationDebounce = 400,
) {
  const text = prettyText(element.textContent.trim());
  const input = createInput(name);
  const code = createCodeSurface(element);
  const error = createErrorStrip();

  element.replaceChildren(...[input, code, error].filter(Boolean));

  const jar = CodeJar(code, highlightJson, {tab: '  '});

  jar.onUpdate(debounce(content => {
    if (input) input.value = content;

    const {valid} = parseText(jar.toString(), code, error, Boolean(input));

    input?.setCustomValidity(valid ? '' : 'Invalid JSON');
  }, revalidationDebounce));
  jar.updateCode(text);

  return {
    /**
     * Replaces the editor content with the given JSON text.
     * @param {string} value - The JSON text to display.
     */
    setText: (value) => { jar.updateCode(value); clearError(code, error); },
    /**
     * Returns the raw editor text.
     * @returns {string} The current editor content.
     */
    getText: () => jar.toString(),
    /**
     * Parses the editor content, reporting the outcome in the error strip.
     * @returns {{valid: boolean, value: (*|undefined)}} The parse outcome.
     */
    read: () => parseText(jar.toString(), code, error, Boolean(input)),
  };
}
