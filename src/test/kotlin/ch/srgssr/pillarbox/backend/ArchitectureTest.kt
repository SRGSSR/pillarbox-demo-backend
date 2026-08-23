package ch.srgssr.pillarbox.backend

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import io.kotest.core.spec.style.ShouldSpec

private const val BASE_PACKAGE = "ch.srgssr.pillarbox.backend"
private const val ADAPTER_PACKAGE = "$BASE_PACKAGE.adapter"

/**
 * Pins the dependency direction of the architecture: the domain knows no framework and no
 * outer layer, the application layer knows no adapter, adapters do not know each other,
 * and bootstrap is referenced by nobody.
 */
class ArchitectureTest :
  ShouldSpec({
    should("keep the domain free of framework imports") {
      Konsist
        .scopeFromPackage("$BASE_PACKAGE.domain..", sourceSetName = "main")
        .imports
        .assertFalse { it.name.startsWith("org.jetbrains.exposed") || it.name.startsWith("io.ktor") }
    }

    should("keep the domain free of outer-layer imports") {
      Konsist
        .scopeFromPackage("$BASE_PACKAGE.domain..", sourceSetName = "main")
        .imports
        .assertFalse {
          it.name.startsWith("$BASE_PACKAGE.application.") ||
            it.name.startsWith("$ADAPTER_PACKAGE.") ||
            it.name.startsWith("$BASE_PACKAGE.bootstrap.")
        }
    }

    should("keep the domain model free of other domain subpackages") {
      Konsist
        .scopeFromPackage("$BASE_PACKAGE.domain.model..", sourceSetName = "main")
        .imports
        .assertFalse {
          it.name.startsWith("$BASE_PACKAGE.domain.playback.") ||
            it.name.startsWith("$BASE_PACKAGE.domain.catalog.") ||
            it.name.startsWith("$BASE_PACKAGE.domain.port.")
        }
    }

    should("keep bootstrap unreferenced outside bootstrap") {
      Konsist
        .scopeFromProduction(sourceSetName = "main")
        .files
        .filterNot {
          it.packagee
            ?.name
            .orEmpty()
            .startsWith("$BASE_PACKAGE.bootstrap")
        }.assertFalse { file ->
          file.imports.any { it.name.startsWith("$BASE_PACKAGE.bootstrap.") }
        }
    }

    should("keep the application layer free of adapter imports") {
      Konsist
        .scopeFromPackage("ch.srgssr.pillarbox.backend.application..", sourceSetName = "main")
        .imports
        .assertFalse { it.name.startsWith("$ADAPTER_PACKAGE.") }
    }

    should("keep adapters from importing other adapters") {
      Konsist
        .scopeFromPackage("$ADAPTER_PACKAGE..", sourceSetName = "main")
        .files
        .assertFalse { file ->
          val adapter =
            file.packagee
              ?.name
              .orEmpty()
              .substringAfter("$ADAPTER_PACKAGE.")
              .substringBefore(".")
          file.imports.any {
            it.name.startsWith("$ADAPTER_PACKAGE.") && !it.name.startsWith("$ADAPTER_PACKAGE.$adapter.")
          }
        }
    }
  })
