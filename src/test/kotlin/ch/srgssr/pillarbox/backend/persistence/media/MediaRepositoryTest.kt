package ch.srgssr.pillarbox.backend.persistence.media

import ch.srgssr.pillarbox.backend.domain.model.Media
import ch.srgssr.pillarbox.backend.domain.model.MediaMetadata
import ch.srgssr.pillarbox.backend.test.MediaLibrary
import ch.srgssr.pillarbox.backend.test.testApplicationContext
import ch.srgssr.pillarbox.backend.test.testDb
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private fun media(
  id: String,
  title: String? = null,
  subtitle: String? = null,
  description: String? = null,
  tags: List<String> = emptyList(),
  deleted: Boolean = false,
  expiresAt: Instant? = null,
) = Media(
  id = id,
  tags = tags,
  sources = listOf(MediaLibrary.Dash),
  metadata = MediaMetadata(title = title, subtitle = subtitle, description = description),
  deleted = deleted,
  expiresAt = expiresAt,
)

class MediaRepositoryTest :
  ShouldSpec({
    val repository = MediaRepository(testDb)

    should("match media by title") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "alps", title = "Sunrise over the Alps"))
        repository.save(media(id = "city", title = "City nightlife"))

        repository.search("alps").items.map { it.id } shouldContainExactly listOf("alps")
      }
    }

    should("match media by tag, subtitle, and description") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "tagged", title = "Untitled", tags = listOf("documentary", "nature")))
        repository.save(media(id = "subtitled", title = "Untitled", subtitle = "A wildlife journey"))
        repository.save(media(id = "described", title = "Untitled", description = "Filmed across the savannah"))

        repository.search("documentary").items.map { it.id } shouldContainExactly listOf("tagged")
        repository.search("wildlife").items.map { it.id } shouldContainExactly listOf("subtitled")
        repository.search("savannah").items.map { it.id } shouldContainExactly listOf("described")
      }
    }

    should("match partial words as a prefix for search-as-you-type") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "glacier", title = "Glacier hike"))
        repository.save(media(id = "city", title = "City nightlife"))

        repository.search("glac").items.map { it.id } shouldContainExactly listOf("glacier")
        repository.search("glacier hi").items.map { it.id } shouldContainExactly listOf("glacier")
      }
    }

    should("match media by identifier") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "special-promo", title = "Untitled"))
        repository.save(media(id = "regular", title = "Untitled"))

        repository.search("promo").items.map { it.id } shouldContainExactly listOf("special-promo")
      }
    }

    should("rank a title match above a description-only match") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "in-description", title = "Something else", description = "A story about whales"))
        repository.save(media(id = "in-title", title = "Whales of the deep"))

        repository.search("whales").items.map { it.id } shouldContainExactly listOf("in-title", "in-description")
      }
    }

    should("require all terms of a multi-word query to match, across fields") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "both", title = "Mountain trails", description = "alpine routes"))
        repository.save(media(id = "one", title = "Mountain bikes"))

        // Terms are AND-ed: only the item carrying both matches.
        repository.search("mountain alpine").items.map { it.id } shouldContainExactly listOf("both")
        repository.search("mountain").items.map { it.id } shouldContainExactlyInAnyOrder listOf("both", "one")
      }
    }

    should("scope search with an additional filter") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "live", title = "Glacier hike"))
        repository.save(media(id = "binned", title = "Glacier descent", deleted = true))

        repository.search("glacier").items.map { it.id } shouldContainExactlyInAnyOrder listOf("live", "binned")
        repository
          .search("glacier", filter = { MediaTable.deleted eq false })
          .items
          .map { it.id } shouldContainExactly listOf("live")
      }
    }

    should("keep expired media out of the playable visibility only") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "undated", title = "Glacier hike"))
        repository.save(media(id = "future", title = "Glacier walk", expiresAt = Clock.System.now() + 1.hours))
        repository.save(media(id = "expired", title = "Glacier melt", expiresAt = Clock.System.now() - 1.hours))
        repository.save(media(id = "binned", title = "Glacier descent", deleted = true))

        // Listing and searching narrow through the same predicate.
        for (query in listOf(null, "glacier")) {
          repository
            .findMedia(query, filter = { MediaVisibility.PLAYABLE })
            .map { it.id } shouldContainExactlyInAnyOrder listOf("undated", "future")
          repository
            .findMedia(query, filter = { MediaVisibility.ACTIVE })
            .map { it.id } shouldContainExactlyInAnyOrder listOf("undated", "future", "expired")
          repository
            .findMedia(query, filter = { MediaVisibility.DELETED })
            .map { it.id } shouldContainExactly listOf("binned")
        }
      }
    }

    should("report the total count independently of the page size") {
      testApplicationContext {
        startApplication()
        repeat(3) { repository.save(media(id = "doc-$it", title = "Documentary $it")) }

        val page = repository.search("documentary", limit = 2)
        page.items shouldHaveSize 2
        page.totalCount shouldBe 3
      }
    }

    should("return an empty result for a blank query") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "any", title = "Anything"))

        val result = repository.search("   ")
        result.items.shouldBeEmpty()
        result.totalCount shouldBe 0
      }
    }

    should("return an empty result when nothing matches") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "any", title = "Anything"))

        repository.search("nonexistentterm").items.shouldBeEmpty()
      }
    }
  })
