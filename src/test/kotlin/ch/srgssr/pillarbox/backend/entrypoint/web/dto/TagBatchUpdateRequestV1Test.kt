package ch.srgssr.pillarbox.backend.entrypoint.web.dto

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class TagBatchUpdateRequestV1Test :
  ShouldSpec({
    should("return the same list if there are no operations") {
      val initialTags = listOf("apple", "banana")
      val request = TagBatchUpdateRequestV1(operations = emptyList())

      request.apply(initialTags) shouldBe initialTags
    }

    should("handle a simple ADD operation") {
      val request =
        TagBatchUpdateRequestV1(
          operations =
            listOf(
              TagOperationV1(TagActionV1.ADD, listOf("cherry", "pear")),
            ),
        )
      val result = request.apply(listOf("apple"))

      result shouldContainExactly listOf("apple", "cherry", "pear")
    }

    should("handle a simple REMOVE operation") {
      val request =
        TagBatchUpdateRequestV1(
          operations =
            listOf(
              TagOperationV1(TagActionV1.REMOVE, listOf("apple")),
            ),
        )
      val result = request.apply(listOf("apple", "banana"))

      result shouldContainExactly listOf("banana")
    }

    should("result in tag being REMOVED when REMOVE comes after ADD") {
      val request =
        TagBatchUpdateRequestV1(
          operations =
            listOf(
              TagOperationV1(TagActionV1.ADD, listOf("test-tag")),
              TagOperationV1(TagActionV1.REMOVE, listOf("test-tag")),
            ),
        )
      val result = request.apply(listOf("initial"))

      result shouldContainExactly listOf("initial")
    }

    should("result in tag being present when ADD comes after REMOVE") {
      val request =
        TagBatchUpdateRequestV1(
          operations =
            listOf(
              TagOperationV1(TagActionV1.REMOVE, listOf("test-tag")),
              TagOperationV1(TagActionV1.ADD, listOf("test-tag")),
            ),
        )

      val result = request.apply(listOf("test-tag"))

      result shouldContainExactly listOf("test-tag")
    }

    should("automatically de-duplicate tags via the distinct() call") {
      val request =
        TagBatchUpdateRequestV1(
          operations =
            listOf(
              TagOperationV1(TagActionV1.ADD, listOf("apple", "apple", "banana")),
            ),
        )
      val result = request.apply(listOf("apple"))

      result shouldHaveSize 2
      result shouldContainExactly listOf("apple", "banana")
    }

    should("do nothing when trying to REMOVE a tag that doesn't exist") {
      val request =
        TagBatchUpdateRequestV1(
          operations =
            listOf(
              TagOperationV1(TagActionV1.REMOVE, listOf("ghost-tag")),
            ),
        )
      val result = request.apply(listOf("existing-tag"))

      result shouldContainExactly listOf("existing-tag")
    }

    should("handle adding to an initially empty list") {
      val request =
        TagBatchUpdateRequestV1(
          operations =
            listOf(
              TagOperationV1(TagActionV1.ADD, listOf("new-tag")),
            ),
        )
      request.apply(emptyList()) shouldContainExactly listOf("new-tag")
    }
  })
