package ch.srgssr.pillarbox.backend.domain.model

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class UserTest :
  ShouldSpec({
    val editorUser = User(oidcSub = "sub", displayName = "User", roles = setOf(Role.WRITE))
    val adminUser = User(oidcSub = "sub", displayName = "User", roles = setOf(Role.ADMIN))

    should("return true when user holds the required role") {
      editorUser.hasAnyRole(setOf(Role.WRITE)).shouldBeTrue()
    }

    should("return true when user holds one of several required roles") {
      editorUser.hasAnyRole(setOf(Role.ADMIN, Role.WRITE)).shouldBeTrue()
    }

    should("return false when user holds none of the required roles") {
      editorUser.hasAnyRole(setOf(Role.ADMIN)).shouldBeFalse()
    }

    should("return false when required roles set is empty") {
      editorUser.hasAnyRole(emptySet()).shouldBeFalse()
    }

    should("return false when user has no roles") {
      User(oidcSub = "sub", displayName = "User").hasAnyRole(setOf(Role.WRITE)).shouldBeFalse()
    }

    should("return true when the user holds an implied role") {
      adminUser.hasAnyRole(setOf(Role.WRITE)).shouldBeTrue()
    }
  })
