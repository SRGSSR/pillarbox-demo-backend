import "htmx.org";

document.addEventListener("htmx:confirm", function(e) {
  if (!e.detail.question) return;

  e.preventDefault();

  const template = document.getElementById('confirm-dialog-template');
  const clone = template.content.cloneNode(true);
  const dialog = clone.querySelector('dialog');

  dialog.querySelector('#confirm-message').textContent = e.detail.question;
  document.body.appendChild(dialog);
  dialog.addEventListener('close', () => {
    if (dialog.returnValue === 'confirm') {
      e.detail.issueRequest(true);
    }
    dialog.remove();
  });

  dialog.showModal();
});
