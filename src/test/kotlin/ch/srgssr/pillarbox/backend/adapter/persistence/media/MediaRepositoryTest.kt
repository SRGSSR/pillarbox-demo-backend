package ch.srgssr.pillarbox.backend.adapter.persistence.media

import ch.srgssr.pillarbox.backend.domain.catalog.MediaCriteria
import ch.srgssr.pillarbox.backend.domain.catalog.MediaVisibility
import ch.srgssr.pillarbox.backend.domain.catalog.QuerySlice
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

    /**
     * Pages the catalogue with a search [text] over every visibility.
     *
     * @param text The full-text query.
     * @param limit The page size.
     * @return The ids of the matching media, most relevant first.
     */
    suspend fun search(
      text: String,
      limit: Int = 100,
    ) = repository.page(MediaCriteria(visibility = MediaVisibility.ANY, text = text), QuerySlice(limit = limit))

    should("match media by title") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "alps", title = "Sunrise over the Alps"))
        repository.save(media(id = "city", title = "City nightlife"))

        search("alps").items.map { it.id } shouldContainExactly listOf("alps")
      }
    }

    should("match media by tag, subtitle, and description") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "tagged", title = "Untitled", tags = listOf("documentary", "nature")))
        repository.save(media(id = "subtitled", title = "Untitled", subtitle = "A wildlife journey"))
        repository.save(media(id = "described", title = "Untitled", description = "Filmed across the savannah"))

        search("documentary").items.map { it.id } shouldContainExactly listOf("tagged")
        search("wildlife").items.map { it.id } shouldContainExactly listOf("subtitled")
        search("savannah").items.map { it.id } shouldContainExactly listOf("described")
      }
    }

    should("match partial words as a prefix for search-as-you-type") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "glacier", title = "Glacier hike"))
        repository.save(media(id = "city", title = "City nightlife"))

        search("glac").items.map { it.id } shouldContainExactly listOf("glacier")
        search("glacier hi").items.map { it.id } shouldContainExactly listOf("glacier")
      }
    }

    should("match media by identifier") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "special-promo", title = "Untitled"))
        repository.save(media(id = "regular", title = "Untitled"))

        search("promo").items.map { it.id } shouldContainExactly listOf("special-promo")
      }
    }

    should("rank a title match above a description-only match") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "in-description", title = "Something else", description = "A story about whales"))
        repository.save(media(id = "in-title", title = "Whales of the deep"))

        search("whales").items.map { it.id } shouldContainExactly listOf("in-title", "in-description")
      }
    }

    should("require all terms of a multi-word query to match, across fields") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "both", title = "Mountain trails", description = "alpine routes"))
        repository.save(media(id = "one", title = "Mountain bikes"))

        // Terms are AND-ed: only the item carrying both matches.
        search("mountain alpine").items.map { it.id } shouldContainExactly listOf("both")
        search("mountain").items.map { it.id } shouldContainExactlyInAnyOrder listOf("both", "one")
      }
    }

    should("scope search with the criteria visibility") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "live", title = "Glacier hike"))
        repository.save(media(id = "binned", title = "Glacier descent", deleted = true))

        search("glacier").items.map { it.id } shouldContainExactlyInAnyOrder listOf("live", "binned")
        repository
          .page(MediaCriteria(text = "glacier"), QuerySlice())
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
        for (text in listOf(null, "glacier")) {
          repository
            .page(MediaCriteria(visibility = MediaVisibility.PLAYABLE, text = text), QuerySlice())
            .items
            .map { it.id } shouldContainExactlyInAnyOrder listOf("undated", "future")
          repository
            .page(MediaCriteria(visibility = MediaVisibility.ACTIVE, text = text), QuerySlice())
            .items
            .map { it.id } shouldContainExactlyInAnyOrder listOf("undated", "future", "expired")
          repository
            .page(MediaCriteria(visibility = MediaVisibility.DELETED, text = text), QuerySlice())
            .items
            .map { it.id } shouldContainExactly listOf("binned")
        }
      }
    }

    should("agree with the domain playability rule over a fixture set") {
      testApplicationContext {
        startApplication()
        val now = Clock.System.now()
        val fixtures =
          listOf(
            media(id = "undated"),
            media(id = "future", expiresAt = now + 1.hours),
            media(id = "expired", expiresAt = now - 1.hours),
            media(id = "binned", deleted = true),
            media(id = "binned-future", deleted = true, expiresAt = now + 1.hours),
          )
        fixtures.forEach { repository.save(it) }

        // Pins the SQL predicate to Media.isPlayable, so the rule cannot drift apart.
        repository
          .page(MediaCriteria(visibility = MediaVisibility.PLAYABLE), QuerySlice())
          .items
          .map { it.id } shouldContainExactlyInAnyOrder fixtures.filter { it.isPlayable(now) }.map { it.id }
      }
    }

    should("report the total count independently of the page size") {
      testApplicationContext {
        startApplication()
        repeat(3) { repository.save(media(id = "doc-$it", title = "Documentary $it")) }

        val page = search("documentary", limit = 2)
        page.items shouldHaveSize 2
        page.totalCount shouldBe 3
      }
    }

    should("list without searching for a blank query") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "any", title = "Anything"))

        search("   ").items.map { it.id } shouldContainExactly listOf("any")
      }
    }

    should("return an empty result for a term-less query") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "any", title = "Anything"))

        val result = search("?!*")
        result.items.shouldBeEmpty()
        result.totalCount shouldBe 0
      }
    }

    should("return an empty result when nothing matches") {
      testApplicationContext {
        startApplication()
        repository.save(media(id = "any", title = "Anything"))

        search("nonexistentterm").items.shouldBeEmpty()
      }
    }
  })
