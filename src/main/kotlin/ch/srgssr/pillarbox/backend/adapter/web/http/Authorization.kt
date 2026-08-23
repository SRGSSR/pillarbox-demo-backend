package ch.srgssr.pillarbox.backend.adapter.web.http

import ch.srgssr.pillarbox.backend.domain.model.Role
import ch.srgssr.pillarbox.backend.domain.model.User
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.util.AttributeKey

private val UserKey = AttributeKey<User>("AuthenticatedUser")

/**
 * Route-scoped plugin that runs after authentication and stores the [User] principal
 * in the call attributes for convenient access via [ApplicationCall.user].
 *
 * If no principal is present, responds with [HttpStatusCode.Unauthorized] and halts the pipeline.
 *
 * Must be installed inside an `authenticate` block.
 */
val AuthenticatedUserPlugin =
  createRouteScopedPlugin("AuthenticatedUser") {
    on(AuthenticationChecked) { call ->
      val user = call.principal<User>()
      if (user == null) {
        call.respond(HttpStatusCode.Unauthorized)
      } else {
        call.attributes.put(UserKey, user)
      }
    }
  }

/**
 * Retrieves the authenticated [User] from the call attributes.
 *
 * Requires [AuthenticatedUserPlugin] to be installed on the route.
 *
 * @throws IllegalStateException if the attribute has not been set.
 */
val ApplicationCall.user: User
  get() = attributes[UserKey]

/**
 * Route-scoped plugin that enforces role-based access control.
 *
 * Runs after authentication and responds with [HttpStatusCode.Forbidden]
 * if the principal does not hold any of the roles specified in [RoleAuthConfig].
 *
 * Typically installed via the [withRole] DSL helper rather than directly.
 */
val RoleAuthorizationPlugin =
  createRouteScopedPlugin(
    name = "RoleAuthorization",
    createConfiguration = { RoleAuthConfig() },
  ) {
    on(AuthenticationChecked) { call ->
      if (call.principal<User>()?.hasAnyRole(pluginConfig.roles) != true) {
        call.respond(HttpStatusCode.Forbidden)
      }
    }
  }

/**
 * Configuration for [RoleAuthorizationPlugin].
 *
 * @property roles The set of accepted roles. Access is granted if the user holds at least one.
 */
class RoleAuthConfig {
  var roles: Set<Role> = emptySet()
}

/**
 * Restricts the enclosed route subtree to users holding at least one of the given [roles].
 *
 * Installs [RoleAuthorizationPlugin] on a child route scope, so each call site gets its own
 * independent role requirement.
 *
 * ```kotlin
 * authenticate("session") {
 *     install(AuthenticatedUserPlugin)
 *     withRole(Role.ADMIN, Role.MODERATOR) {
 *         get("/reports") { /* accessible to either role */ }
 *     }
 * }
 * ```
 *
 * @param roles One or more roles, any of which grants access.
 * @param build The route subtree to protect.
 */
fun Route.withRole(
  vararg roles: Role,
  build: Route.() -> Unit,
) {
  require(roles.isNotEmpty()) { "roles must not be empty" }

  val route =
    createChild(
      object : RouteSelector() {
        override suspend fun evaluate(
          context: RoutingResolveContext,
          segmentIndex: Int,
        ) = RouteSelectorEvaluation.Transparent
      },
    )
  route.install(RoleAuthorizationPlugin) {
    this.roles = roles.toSet()
  }
  route.build()
}
