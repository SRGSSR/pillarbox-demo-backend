package ch.srgssr.pillarbox.backend.test

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking

class JsonSchemaMatcher(
  private val schemaPath: String,
) : Matcher<HttpResponse> {
  companion object {
    private val registry: SchemaRegistry =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
  }

  override fun test(value: HttpResponse): MatcherResult {
    val jsonString = runBlocking { value.bodyAsText() }

    val schema = registry.getSchema(SchemaLocation.of("classpath:$schemaPath"))
    val result =
      schema.validate(jsonString, InputFormat.JSON) { context ->
        context.executionConfig { config ->
          config.formatAssertionsEnabled(true)
        }
      }

    return MatcherResult(
      passed = result.isEmpty(),
      failureMessageFn = {
        val details = result.joinToString("\n") { "[${it.instanceLocation}] ${it.message}" }
        "Response did not match schema at $schemaPath:\n$details"
      },
      negatedFailureMessageFn = { "Response should NOT have matched schema $schemaPath" },
    )
  }
}

infix fun HttpResponse.shouldMatchSchema(path: String): HttpResponse {
  this should JsonSchemaMatcher(path)
  return this
}
