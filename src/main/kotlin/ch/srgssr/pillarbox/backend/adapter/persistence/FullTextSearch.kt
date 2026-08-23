package ch.srgssr.pillarbox.backend.adapter.persistence

import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.FloatColumnType
import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.stringLiteral

/**
 * Full-text search against a `tsvector` column that PostgreSQL generates and indexes.
 *
 * The column is not part of the Exposed [Table] definition; this helper renders the `WHERE` and
 * `ORDER BY` fragments that reference it by name.
 *
 * @param table The table carrying the vector column.
 * @param column The database name of the `tsvector` column.
 * @property config The text-search configuration queries are parsed with; must match the one the
 *   vector is generated with. The `simple` default skips stemming, keeping identifiers intact.
 */
class FullTextSearch(
  table: Table,
  column: String,
  private val config: String = "simple",
) {
  private val vector = "\"${table.tableName}\".\"$column\""

  /**
   * Builds a `WHERE` predicate matching rows against [query].
   *
   * @param query Free-text search input; every term must match as a prefix ("glac" finds "glacier").
   * @return An [Op] that is `true` for rows whose vector matches [query].
   */
  infix fun matches(query: String): Op<Boolean> = MatchOp(query)

  /**
   * Builds a relevance score for [query], for use in `ORDER BY`.
   *
   * @param query Free-text search input, tokenised like [matches].
   * @return A float [Expression] of the row's `ts_rank` against [query].
   */
  fun rank(query: String): Expression<Float> = RankFunction(query)

  /**
   * Whether [query] carries at least one term that can be searched.
   *
   * @param query Free-text search input.
   * @return `true` if [query] holds a letter-or-digit run; `false` for blank or punctuation-only
   *   input, which would tokenise to an empty `tsquery`.
   */
  fun hasTerms(query: String): Boolean = TERM_PATTERN.containsMatchIn(query)

  /** Appends a `to_tsquery(config, …)` call with the prefix query bound as a parameter. */
  private fun QueryBuilder.appendTsQuery(query: String) {
    append("to_tsquery(")
    append(stringLiteral(config))
    append(", ")
    registerArgument(TextColumnType(), toPrefixTsQuery(query))
    append(")")
  }

  private inner class MatchOp(
    private val query: String,
  ) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
      queryBuilder {
        append(vector)
        append(" @@ ")
        appendTsQuery(query)
      }
    }
  }

  private inner class RankFunction(
    private val query: String,
  ) : Function<Float>(FloatColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
      queryBuilder {
        append("ts_rank(")
        append(vector)
        append(", ")
        appendTsQuery(query)
        append(")")
      }
    }
  }

  companion object {
    /** Matches maximal runs of letters or digits, used to tokenise a free-text query. */
    private val TERM_PATTERN = Regex("[\\p{L}\\p{N}]+")

    /**
     * Turns free text into a prefix `tsquery` string such as `glac:* & alp:*`.
     *
     * Only letters and digits survive tokenisation, so the result is always a well-formed,
     * injection-safe `tsquery` (an all-punctuation input yields an empty string).
     *
     * @return The `to_tsquery` text for [query]; terms are AND-ed and matched as prefixes.
     */
    private fun toPrefixTsQuery(query: String): String =
      TERM_PATTERN
        .findAll(query)
        .joinToString(" & ") { "${it.value}:*" }
  }
}
