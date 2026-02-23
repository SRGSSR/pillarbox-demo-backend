package ch.srgssr.pillarbox.backend.entrypoint.web.dto

import kotlinx.serialization.Serializable

/**
 * A request object used to perform multiple tag modifications in a single batch.
 *
 * This is used by the to update media metadata efficiently. It allows for complex
 * sequences of additions and removals in one request.
 *
 * @property operations A sequential list of [TagOperationV1] to apply.
 */
@Serializable
data class TagBatchUpdateRequestV1(
  val operations: List<TagOperationV1>,
) {
  /**
   * Applies the sequence of [operations] to an existing list of tags.
   *
   * The operations are processed in the order they appear in the [operations] list.
   * The final result is de-duplicated to ensure unique tags.
   *
   * @param tags The current list of tags (e.g., from a domain model).
   *
   * @return A new, modified, and distinct list of tags.
   */
  fun apply(tags: List<String>): List<String> {
    val result = tags.toMutableList()

    operations.forEach { op ->
      when (op.action) {
        TagActionV1.ADD -> result.addAll(op.tags)
        TagActionV1.REMOVE -> result.removeAll(op.tags.toSet())
      }
    }

    return result.distinct()
  }
}

/**
 * Defines the type of modification to perform on a collection of tags.
 */
@Serializable
enum class TagActionV1 {
  /** Adds new tags to the existing collection. */
  ADD,

  /** Removes specific tags from the existing collection. */
  REMOVE,
}

/**
 * Represents a single modification step in a tag update process.
 *
 * @property action The [TagActionV1] to perform (ADD or REMOVE).
 * @property tags The list of tag strings to be used in this operation.
 */
@Serializable
data class TagOperationV1(
  val action: TagActionV1,
  val tags: List<String>,
)
