import htmx from "htmx.org";

/**
 * Moves media to a folder in a single request. The response removes their
 * cards from the list and reports the outcome in the snackbar.
 * @param {string[]} mediaIds - The ids of the media to move.
 * @param {string} folderId - The id of the target folder.
 * @returns {Promise<void>} Resolves once the request has completed.
 */
export function assignMedia(mediaIds, folderId) {
  return htmx.ajax("POST", `/console/actions/folder/${folderId}/media`, {
    values: { mediaId: mediaIds },
    swap: "none",
  });
}

/**
 * Removes media from a folder in a single request. The media stay in the
 * library, unassigned. The response removes their cards from the list and
 * reports the outcome in the snackbar.
 * @param {string[]} mediaIds - The ids of the media to remove.
 * @param {string} folderId - The id of the folder that holds them.
 * @returns {Promise<void>} Resolves once the request has completed.
 */
export function removeMedia(mediaIds, folderId) {
  return htmx.ajax("DELETE", `/console/actions/folder/${folderId}/media`, {
    values: { mediaId: mediaIds },
    swap: "none",
  });
}
