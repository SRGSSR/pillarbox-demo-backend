import htmx from 'htmx.org';
import dot from 'dot-object';

htmx.defineExtension('dot-json', {
  encodeParameters: function (xhr, parameters, elt) {
    xhr.setRequestHeader("Content-Type", "application/json");

    const coercedParams = {};

    elt.querySelectorAll('[name]').forEach(input => {
      const name = input.getAttribute('name');
      let value = input.value;

      if (value === "" || value === null) return;

      if (input.type === 'number' || input.hasAttribute('data-number')) {
        value = Number(value);
      } else if (input.type === 'checkbox') {
        value = input.checked;
      } else if (input.getAttribute('data-type') === 'json') {
        try {
          value = JSON.parse(value);
        } catch (e) {
          return;
        }
      } else if (input.getAttribute('data-type') === 'array') {
        value = value.split(',').map(v => v.trim());
      }

      coercedParams[name] = value;
    });

    return JSON.stringify(dot.object(coercedParams), (key, value) => {
      if (!Array.isArray(value)) return value

      return value.filter(item => item !== null && item !== undefined);
    });
  }
});

document.addEventListener('htmx:configRequest', (evt) => {
  const {indexSelector: selector} = evt.detail.elt.dataset;
  if (!selector) return;

  const index = document.querySelector(selector)?.dataset?.index

  evt.detail.parameters['index'] = !index ? 0 : parseInt(index) + 1;
});
