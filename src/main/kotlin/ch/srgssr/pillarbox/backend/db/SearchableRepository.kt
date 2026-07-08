package ch.srgssr.pillarbox.backend.db

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * An [ExposedRepository] over a table carrying a full-text search vector.
 *
 * @param textSearch The [FullTextSearch] bound to the table's `tsvector` column.
 */
abstract class SearchableRepository<T, ID>(
  db: Database,
  table: Table,
  idColumn: Column<ID>,
  private val textSearch: FullTextSearch,
) : ExposedRepository<T, ID>(db = db, table = table, idColumn = idColumn) {
  /**
   * Retrieves a page of entities matching the full-text [query], most relevant first.
   *
   * @param query Free-text search input; each term matches as a prefix. Blank or term-less input
   *   (e.g. punctuation only) yields an empty result without hitting the database.
   * @param limit The maximum number of items to return.
   * @param offset The number of items to skip before returning results.
   * @param filter An optional additional WHERE predicate.
   * @param sort Optional tie-breaking order applied after relevance.
   * @return A [PaginatedResult] of the matching entities.
   */
  suspend fun search(
    query: String,
    limit: Int = 100,
    offset: Long = 0,
    filter: (() -> Op<Boolean>)? = null,
    sort: List<Pair<Expression<*>, SortOrder>> = emptyList(),
  ): PaginatedResult<T> {
    val term = query.trim()
    if (!textSearch.hasTerms(term)) {
      return PaginatedResult(items = emptyList(), totalCount = 0, limit = limit, offset = offset)
    }

    val match = textSearch matches term
    return getAllPaginated(
      limit = limit,
      offset = offset,
      filter = { filter?.let { match and it() } ?: match },
      sort = listOf(textSearch.rank(term) to SortOrder.DESC) + sort + (idColumn to SortOrder.ASC),
    )
  }
}
