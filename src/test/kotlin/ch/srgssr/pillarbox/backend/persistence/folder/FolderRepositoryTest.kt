package ch.srgssr.pillarbox.backend.persistence.folder

import ch.srgssr.pillarbox.backend.domain.model.Folder
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.testDb
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class FolderRepositoryTest :
  ShouldSpec({
    val repository = FolderRepository(testDb)

    should("return ancestors from the root down to the folder itself") {
      testApplicationContext {
        startApplication()

        val root = repository.save(Folder(name = "Root"))
        val mid = repository.save(Folder(name = "Mid", parentId = root.id))
        val leaf = repository.save(Folder(name = "Leaf", parentId = mid.id))

        repository.findAncestors(leaf.id).map { it.id } shouldBe listOf(root.id, mid.id, leaf.id)
        repository.findAncestors(mid.id).map { it.id } shouldBe listOf(root.id, mid.id)
        repository.findAncestors(root.id).map { it.id } shouldBe listOf(root.id)
      }
    }

    should("return an empty list for an unknown folder") {
      testApplicationContext {
        startApplication()

        repository.findAncestors("does-not-exist").shouldBeEmpty()
      }
    }
  })
