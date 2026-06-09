import htmx from "htmx.org";

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

/**
 * Opens the shared rename dialog pre-filled for the selected folder, wiring its
 * form to the matching hx-patch action target.
 * @listens document#click
 */
document.addEventListener("click", function(e) {
  const trigger = e.target.closest("[data-rename-folder]");

  if (!trigger) return;

  const { folderId, folderName } = trigger.dataset;
  const dialog = document.getElementById("rename-folder-dialog");
  const form = dialog.querySelector("form");

  dialog.querySelector("#rename-folder-name").value = folderName;
  form.setAttribute("hx-patch", `/console/actions/folder/${folderId}`);
  form.setAttribute("hx-target", `[id='folder-card-${folderId}']`);
  htmx.process(form);

  dialog.showModal();
});

/**
 * Closes the rename dialog after a successful submission.
 * @listens document#htmx:afterRequest
 */
document.addEventListener("htmx:afterRequest", function(e) {
  const form = e.target.closest("#rename-folder-dialog form");

  if (!form || !e.detail.successful) return;

  document.getElementById("rename-folder-dialog").close();
});
