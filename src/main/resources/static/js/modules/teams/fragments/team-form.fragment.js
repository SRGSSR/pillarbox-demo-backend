import htmx from "htmx.org";

/**
 * Opens the team dialog once its form fragment has loaded.
 * @listens document#htmx:afterRequest
 */
document.addEventListener("htmx:afterRequest", function(e) {
  if (!e.target.hasAttribute("data-open-team") || !e.detail.successful) return;

  document.getElementById("team-dialog").showModal();
});

/**
 * Keeps Enter in the member search from submitting the team form; members are picked, not typed.
 * @listens document#keydown
 */
document.addEventListener("keydown", function(e) {
  if (e.target.hasAttribute("data-member-search") && e.key === "Enter") e.preventDefault();
});

/**
 * Adds the chosen user as a pending member row, fetched from the server so the row markup lives in
 * one place. Skips users already in the list and clears the search box.
 * @listens document#input
 */
document.addEventListener("input", function(e) {
  const input = e.target;

  if (!input.hasAttribute("data-member-search")) return;

  const option = [...input.list.options].find((o) => o.value === input.value);

  if (!option) return;

  const oidcSub = option.dataset.id;
  const list = input.closest("form").querySelector("#member-list");

  input.value = "";

  if (list.querySelector(`[data-member-id="${CSS.escape(oidcSub)}"]`)) return;

  htmx.ajax("GET", `/console/fragments/member-row?oidcSub=${encodeURIComponent(oidcSub)}`, {
    target: list,
    swap: "beforeend",
  });
});

/**
 * Removes a pending member row; nothing is persisted until the team is saved.
 * @listens document#click
 */
document.addEventListener("click", function(e) {
  const remove = e.target.closest("[data-remove-member]");

  if (remove) remove.closest(".member-row").remove();
});

/**
 * Closes the dialog once the team is saved.
 * @listens document#htmx:afterRequest
 */
document.addEventListener("htmx:afterRequest", function(e) {
  if (!e.target.classList.contains("team-form") || !e.detail.successful) return;

  e.target.closest("dialog").close();
});
