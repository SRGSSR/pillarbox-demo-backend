package ch.srgssr.pillarbox.backend.entrypoint.web.console

/**
 * A single `<option>` for a datalist-backed autocomplete input.
 *
 * @property value Text filled into the input (and shown) when the option is chosen.
 * @property label Optional hint shown next to the value in the suggestion dropdown.
 * @property id Optional stable identifier, rendered as `data-id` so a script can submit it
 *   instead of the free-typed [value].
 */
data class DatalistOption(
  val value: String,
  val label: String? = null,
  val id: String? = null,
)
