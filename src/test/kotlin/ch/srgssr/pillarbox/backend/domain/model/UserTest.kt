package ch.srgssr.pillarbox.backend.domain.model

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class UserTest :
  ShouldSpec({
    val readOnlyUser = User(oidcSub = "sub", displayName = "User", roles = setOf(Role.READ))

    should("return true when user holds the required role") {
      readOnlyUser.hasAnyRole(setOf(Role.READ)).shouldBeTrue()
    }

    should("return true when user holds one of several required roles") {
      readOnlyUser.hasAnyRole(setOf(Role.READ, Role.WRITE)).shouldBeTrue()
    }

    should("return false when user holds none of the required roles") {
      readOnlyUser.hasAnyRole(setOf(Role.WRITE)).shouldBeFalse()
    }

    should("return false when required roles set is empty") {
      readOnlyUser.hasAnyRole(emptySet()).shouldBeFalse()
    }

    should("return false when user has no roles") {
      User(oidcSub = "sub", displayName = "User").hasAnyRole(setOf(Role.READ)).shouldBeFalse()
    }
  })
