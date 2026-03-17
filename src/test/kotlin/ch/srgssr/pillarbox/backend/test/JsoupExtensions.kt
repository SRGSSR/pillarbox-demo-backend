package ch.srgssr.pillarbox.backend.test

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

operator fun Document.get(selector: String): Elements = this.select(selector)

fun Document.count(selector: String): Int = this[selector].size

operator fun Element.get(selector: String): Elements = this.select(selector)
