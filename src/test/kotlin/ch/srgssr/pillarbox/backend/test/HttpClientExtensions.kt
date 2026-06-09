package ch.srgssr.pillarbox.backend.test

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse

suspend fun HttpClient.hxGet(
  urlString: String,
  block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse =
  get(urlString) {
    header("HX-Request", "true")
    block()
  }

suspend fun HttpClient.hxPost(
  urlString: String,
  block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse =
  post(urlString) {
    header("HX-Request", "true")
    block()
  }

suspend fun HttpClient.hxPatch(
  urlString: String,
  block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse =
  patch(urlString) {
    header("HX-Request", "true")
    block()
  }

suspend fun HttpClient.hxDelete(
  urlString: String,
  block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse =
  delete(urlString) {
    header("HX-Request", "true")
    block()
  }
