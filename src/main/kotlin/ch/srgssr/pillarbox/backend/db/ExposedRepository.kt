package ch.srgssr.pillarbox.backend.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpsertBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * Abstract repository providing a bridge between domain entities [T] and the [Table] storage.
 *
 * This class handles common CRUD operations using the Exposed framework and ensures that these
 * operations are performed within the appropriate transaction context.
 *
 * @param T The domain model type.
 * @param ID The type of the unique identifier (e.g., String, Int, UUID).
 * @property db The [Database] instance to execute queries against.
 * @property table The Exposed [Table] definition.
 * @property idColumn The specific [Column] used as the primary lookup key.
 */
abstract class ExposedRepository<T, ID>(
  protected val db: Database,
  protected val table: Table,
  protected val idColumn: Column<ID>,
) {
  /**
   * Maps a database [ResultRow] back into the domain entity [T].
   */
  protected abstract fun ResultRow.decode(): T

  /**
   * Maps a domain entity [T] onto the [UpdateBuilder] for persistence.
   */
  protected abstract fun Table.encode(
    builder: UpdateBuilder<*>,
    item: T,
  )

  /**
   * Specialized mapper for a domain entity [T] onto the [UpdateBuilder] for updating.
   */
  protected open fun encodeOnUpdate(item: T): (UpsertBuilder.(UpdateStatement) -> Unit)? = null

  /**
   * Executes a database [block] within a suspended transaction.
   *
   *  @param readOnly If true, optimizes the transaction for read operations.
   */
  protected suspend fun <R> query(
    readOnly: Boolean = false,
    block: suspend Transaction.() -> R,
  ): R = suspendTransaction(db = db, readOnly = readOnly) { block() }

  /**
   * Finds a specific resource by its unique identifier.
   *
   * @param id The unique identifier of the entity.
   *
   * @return The entity [T] if found, or null if no match exists.
   */
  open suspend fun find(id: ID): T? =
    query(readOnly = true) {
      table
        .selectAll()
        .where { idColumn eq id }
        .map { it.decode() } // Uses the internal hook
        .singleOrNull()
    }

  /**
   * Whether a specific resource exists with this unique identifier.
   *
   * @param id The unique identifier of the entity.
   *
   * @return true if it exists, false otherwise.
   */
  open suspend fun exists(id: ID): Boolean =
    query(readOnly = true) {
      table
        .select(idColumn)
        .where { idColumn eq id }
        .count() > 0
    }

  /**
   * Finds a single resource based on a provided filter.
   *
   * @param filter A lambda returning the [Op<Boolean>] (WHERE clause).
   * @return The entity [T] if found, or null if no match exists.
   */
  open suspend fun findOne(filter: Table.() -> Op<Boolean>): T? =
    query(readOnly = true) {
      table
        .selectAll()
        .where { table.filter() }
        .singleOrNull()
        ?.decode()
    }

  /**
   * Retrieves a paginated stream of all available resources.
   *
   * @param limit The maximum number of items to return.
   * @param offset The number of items to skip for pagination.
   * @param filter An optional WHERE predicate applied to the query.
   * @param sort An optional list of column/direction pairs for ORDER BY.
   *
   * @return A [Flow] emitting the collection of [T] objects.
   */
  open fun getAll(
    limit: Int = 100,
    offset: Long = 0,
    filter: (() -> Op<Boolean>)? = null,
    sort: List<Pair<Expression<*>, SortOrder>>? = null,
  ): Flow<T> =
    channelFlow {
      query(readOnly = true) {
        table
          .selectAll()
          .apply { filter?.let { where(it) } }
          .apply { sort?.let { orderBy(*it.toTypedArray()) } }
          .limit(limit)
          .offset(offset)
          .forEach { row ->
            send(row.decode())
          }
      }
    }

  /**
   * Retrieves a paginated result including the total count of matches.
   *
   * @param limit The maximum number of items to return.
   * @param offset The number of items to skip for pagination.
   * @param filter An optional WHERE predicate applied to the query.
   * @param sort An optional list of column/direction pairs for ORDER BY.
   *
   * @return A [PaginatedResult] with the matching items and the total number of matches.
   */
  open suspend fun getAllPaginated(
    limit: Int = 100,
    offset: Long = 0,
    filter: (() -> Op<Boolean>)? = null,
    sort: List<Pair<Expression<*>, SortOrder>>? = null,
  ): PaginatedResult<T> =
    query(readOnly = true) {
      table
        .selectAll()
        .apply { filter?.let { where(it) } }
        .apply { sort?.let { orderBy(*it.toTypedArray()) } }
        .paginated(limit, offset)
        .map { it.decode() }
    }

  /**
   * Persists or overwrites a resource using an upsert operation.
   *
   * @param item The entity to save.
   */
  open suspend fun save(item: T): T =
    query {
      val stmt =
        table.upsert(onUpdate = encodeOnUpdate(item)) {
          encode(it, item)
        }
      val id = stmt[idColumn]
      table
        .selectAll()
        .where { idColumn eq id }
        .single()
        .decode()
    }

  /**
   * Deletes a resource from the persistence layer.
   *
   * @param id The unique identifier of the entity to delete.
   *
   * @return true if the entity was successfully deleted, false if it did not exist.
   */
  open suspend fun delete(id: ID) =
    query {
      table.deleteWhere { idColumn eq id } > 0
    }
}

/**
 * A generic wrapper for paginated data sets.
 *
 * @param T The type of items being paginated.
 * @property items The slice of data for the current page.
 * @property totalCount The total number of items available across all pages in the database.
 * @property limit The maximum number of items requested for this page (page size).
 * @property offset The starting point in the total data set (usually calculated as `page * limit`).
 */
data class PaginatedResult<T>(
  val items: List<T>,
  val totalCount: Long,
  val limit: Int,
  val offset: Long,
)

/**
 * Executes this query as two statements within the current transaction:
 * a `SELECT COUNT(*)` for the total matches, and a `SELECT … LIMIT/OFFSET`
 * for the current page.
 *
 * @param limit Maximum number of rows to return.
 * @param offset Number of rows to skip before the first result.
 * @return A [PaginatedResult] containing the matched rows and total count.
 */
fun Query.paginated(
  limit: Int,
  offset: Long,
): PaginatedResult<ResultRow> {
  val totalCount = count()
  val items = limit(limit).offset(offset).toList()
  return PaginatedResult(items = items, totalCount = totalCount, limit = limit, offset = offset)
}

/**
 * Transforms the items in this [PaginatedResult] while preserving pagination metadata.
 */
fun <T, R> PaginatedResult<T>.map(transform: (T) -> R) =
  PaginatedResult(
    items = items.map(transform),
    totalCount = totalCount,
    limit = limit,
    offset = offset,
  )
