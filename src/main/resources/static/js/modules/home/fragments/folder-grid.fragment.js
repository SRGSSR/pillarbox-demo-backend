/**
 * Closes the new-folder dialog and resets its form after a successful submission.
 * @listens document#htmx:afterRequest
 */
document.addEventListener("htmx:afterRequest", function(e) {
  const form = e.target.closest("#new-folder-dialog form");

  if (!form || !e.detail.successful) return;

  document.getElementById("new-folder-dialog").close();
  form.reset();
});
