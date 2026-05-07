package ch.srgssr.pillarbox.backend.auth

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.persistence.user.UserRepository
import ch.srgssr.pillarbox.backend.test.buildJwt
import com.auth0.jwt.JWT
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.coVerify
import io.mockk.mockk

class UserManagerTest :
  ShouldSpec({
    should("save user with name claim as display name") {
      val repository = mockk<UserRepository>(relaxed = true)
      val payload = JWT.decode(buildJwt(subject = "test-sub", name = "Test User"))

      UserManager(repository).upsert(payload)

      coVerify { repository.save(match { it.oidcSub == "test-sub" && it.displayName == "Test User" }) }
    }

    should("fall back to subject as display name when name claim is absent") {
      val repository = mockk<UserRepository>(relaxed = true)
      val payload = JWT.decode(buildJwt(subject = "test-sub"))

      UserManager(repository).upsert(payload)

      coVerify { repository.save(match { it.oidcSub == "test-sub" && it.displayName == "test-sub" }) }
    }

    should("parse roles from realm_access claim") {
      val repository = mockk<UserRepository>(relaxed = true)
      val payload = JWT.decode(buildJwt(subject = "test-sub", roles = setOf(Role.READ, Role.WRITE)))

      UserManager(repository).upsert(payload)

      coVerify { repository.save(match { it.roles == setOf(Role.READ, Role.WRITE) }) }
    }

    should("produce empty role set when realm_access claim is absent") {
      val repository = mockk<UserRepository>(relaxed = true)
      val payload = JWT.decode(buildJwt(subject = "test-sub"))

      UserManager(repository).upsert(payload)

      coVerify { repository.save(match { it.roles.isEmpty() }) }
    }
  })
