/**
 * Opens the permissions dialog once its fragment has loaded.
 * @listens document#htmx:afterRequest
 */
document.addEventListener("htmx:afterRequest", function(e) {
  if (!e.target.hasAttribute("data-open-permissions") || !e.detail.successful) return;

  document.getElementById("folder-permissions-dialog").showModal();
});

/**
 * Mirrors the chosen suggestion's stable id (data-id) into the hidden subjectRef field, so the
 * grant is submitted by id rather than by the free-typed name.
 * @listens document#input
 */
document.addEventListener("input", function(e) {
  const input = e.target;

  if (!input.hasAttribute("data-subject-search")) return;

  const option = [...input.list.options].find((o) => o.value === input.value);

  input.form.querySelector("[name='subjectRef']").value = option ? option.dataset.id : "";
});

/**
 * Resets and collapses the add-permission form after a grant is saved.
 * @listens document#htmx:afterRequest
 */
document.addEventListener("htmx:afterRequest", function(e) {
  if (!e.target.classList.contains("permission-add-form") || !e.detail.successful) return;

  e.target.reset();
  e.target.closest("details").open = false;
});
