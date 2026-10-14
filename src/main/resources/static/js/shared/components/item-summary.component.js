/**
 * Lists the given titles in an item summary, or hides the summary when there
 * are none.
 * @param {HTMLUListElement} list - The item summary element.
 * @param {string[]} [titles] - The titles to list.
 */
export function fillItemSummary(list, titles = []) {
  list.hidden = titles.length === 0;
  list.replaceChildren(...titles.map((title) => {
    const item = document.createElement("li");

    item.textContent = title;

    return item;
  }));
}
