package ch.srgssr.pillarbox.backend.test

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

operator fun Document.get(selector: String): Elements = this.select(selector)

fun Document.count(selector: String): Int = this[selector].size

operator fun Element.get(selector: String): Elements = this.select(selector)

/** Wraps a bare `<tr>` fragment in a table so Jsoup's HTML parser keeps the rows. */
fun parseRows(fragment: String): Document = Jsoup.parse("<table>$fragment</table>")
