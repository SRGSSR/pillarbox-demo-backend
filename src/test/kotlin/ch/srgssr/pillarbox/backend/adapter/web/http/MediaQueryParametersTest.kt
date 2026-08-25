package ch.srgssr.pillarbox.backend.adapter.web.http

import ch.srgssr.pillarbox.backend.domain.catalog.FolderScope
import ch.srgssr.pillarbox.backend.domain.catalog.MediaVisibility
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.Parameters
import io.ktor.http.parametersOf

class MediaQueryParametersTest :
  ShouldSpec({
    should("default the visibility to ANY when the parameter is absent") {
      Parameters.Empty.toMediaVisibility() shouldBe MediaVisibility.ANY
    }

    should("parse the explicit visibility values") {
      parametersOf("visibility", "active").toMediaVisibility() shouldBe MediaVisibility.ACTIVE
      parametersOf("visibility", "deleted").toMediaVisibility() shouldBe MediaVisibility.DELETED
    }

    should("return null for an unknown visibility") {
      parametersOf("visibility", "any").toMediaVisibility().shouldBeNull()
      parametersOf("visibility", "ACTIVE").toMediaVisibility().shouldBeNull()
    }

    should("default the scope to Anywhere when the parameter is absent") {
      Parameters.Empty.toFolderScope() shouldBe FolderScope.Anywhere
    }

    should("parse the explicit scope values") {
      parametersOf("scope", "all").toFolderScope() shouldBe FolderScope.Anywhere
      parametersOf("scope", "unassigned").toFolderScope() shouldBe FolderScope.Unassigned
    }

    should("return null for an unknown scope") {
      parametersOf("scope", "anywhere").toFolderScope().shouldBeNull()
    }
  })
