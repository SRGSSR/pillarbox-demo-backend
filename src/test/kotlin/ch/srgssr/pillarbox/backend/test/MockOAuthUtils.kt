package ch.srgssr.pillarbox.backend.test

import ch.srgssr.pillarbox.backend.domain.model.Role
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nimbusds.oauth2.sdk.TokenRequest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.OAuth2Config
import no.nav.security.mock.oauth2.token.OAuth2TokenCallback

fun buildJwtWithRoleList(
  subject: String,
  name: String? = null,
  roles: List<String> = emptyList(),
): String =
  JWT
    .create()
    .withSubject(subject)
    .let { if (name != null) it.withClaim("name", name) else it }
    .let {
      if (roles.isNotEmpty()) {
        it.withClaim("realm_access", mapOf("roles" to roles))
      } else {
        it
      }
    }.sign(Algorithm.none())

fun buildJwt(
  subject: String,
  name: String? = null,
  roles: Set<Role> = emptySet(),
): String = buildJwtWithRoleList(subject, name, roles.map { r -> r.key })

fun mockOAuth2Server(issuerId: String = "pillarbox-realm"): MockOAuth2Server =
  MockOAuth2Server(
    OAuth2Config(
      tokenCallbacks =
        setOf(
          object : OAuth2TokenCallback {
            override fun issuerId() = issuerId

            override fun tokenExpiry() = 3600L

            override fun subject(tokenRequest: TokenRequest) = error("No callback enqueued")

            override fun audience(tokenRequest: TokenRequest) = error("No callback enqueued")

            override fun addClaims(tokenRequest: TokenRequest) = error("No callback enqueued")

            override fun typeHeader(tokenRequest: TokenRequest) = error("No callback enqueued")
          },
        ),
    ),
  )
