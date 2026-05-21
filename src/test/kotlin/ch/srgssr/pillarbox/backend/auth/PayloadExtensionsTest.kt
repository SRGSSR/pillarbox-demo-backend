package ch.srgssr.pillarbox.backend.auth

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.test.buildJwt
import ch.srgssr.pillarbox.backend.test.buildJwtWithRoleList
import com.auth0.jwt.JWT
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class PayloadExtensionsTest :
  ShouldSpec({
    should("parse WRITE role from roles claim") {
      val payload = JWT.decode(buildJwt(subject = "sub", roles = setOf(Role.WRITE)))

      payload.roles shouldBe setOf(Role.WRITE)
    }

    should("produce empty role set when roles claim is absent") {
      val payload = JWT.decode(buildJwt(subject = "sub"))

      payload.roles.shouldBeEmpty()
    }

    should("ignore unrecognised role strings") {
      val payload = JWT.decode(buildJwtWithRoleList(subject = "sub", roles = listOf("root")))

      payload.roles shouldHaveSize 0
    }
  })
