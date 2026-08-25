package ch.srgssr.pillarbox.backend.adapter.persistence.folder

import ch.srgssr.pillarbox.backend.adapter.persistence.media.MediaRepository
import ch.srgssr.pillarbox.backend.domain.model.Folder
import ch.srgssr.pillarbox.backend.test.MediaBuilder
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.testDb
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class FolderRepositoryTest :
  ShouldSpec({
    val repository = FolderRepository(testDb)
    val mediaRepository = MediaRepository(testDb)

    /**
     * Saves a new media item and assigns it to the given folder.
     *
     * @param folderId The folder to assign the media to, or `null` to leave it unassigned.
     * @return The id of the saved media.
     */
    suspend fun addMedia(folderId: String?): String {
      val media = mediaRepository.save(MediaBuilder().build())
      if (folderId != null) repository.assignMedia(folderId, media.id)
      return media.id
    }

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

    should("count media in a folder and all of its descendants") {
      testApplicationContext {
        startApplication()

        val root = repository.save(Folder(name = "Root"))
        val mid = repository.save(Folder(name = "Mid", parentId = root.id))
        val leaf = repository.save(Folder(name = "Leaf", parentId = mid.id))
        val sibling = repository.save(Folder(name = "Sibling", parentId = root.id))
        val empty = repository.save(Folder(name = "Empty"))

        addMedia(root.id)
        addMedia(mid.id)
        addMedia(leaf.id)
        addMedia(leaf.id)
        addMedia(sibling.id)
        val deleted = addMedia(leaf.id)
        mediaRepository.softDelete(deleted)

        repository.countMediaIn(root.id) shouldBe 5
        repository.countMediaIn(mid.id) shouldBe 3
        repository.countMediaIn(leaf.id) shouldBe 2
        repository.countMediaIn(sibling.id) shouldBe 1
        repository.countMediaIn(empty.id) shouldBe 0
        repository.countMediaIn("does-not-exist") shouldBe 0

        repository.countMediaIn(root.id, mid.id, empty.id) shouldBe
          mapOf(root.id to 5L, mid.id to 3L, empty.id to 0L)
      }
    }

    should("count unassigned media for a null folder") {
      testApplicationContext {
        startApplication()

        val folder = repository.save(Folder(name = "Folder"))
        addMedia(folder.id)
        addMedia(null)
        addMedia(null)

        repository.countMediaIn(null) shouldBe 2
        repository.countMediaIn(folder.id, null) shouldBe mapOf<String?, Long>(folder.id to 1L, null to 2L)
      }
    }
  })
