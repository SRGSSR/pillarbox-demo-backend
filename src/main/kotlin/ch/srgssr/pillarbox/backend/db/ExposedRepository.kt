package ch.srgssr.pillarbox.backend.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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
  private val db: Database,
  private val table: Table,
  private val idColumn: Column<ID>,
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
   * Retrieves a paginated stream of all available resources.
   *
   * @param limit The maximum number of items to return.
   * @param offset The number of items to skip for pagination.
   *
   * @return A [Flow] emitting the collection of [T] objects.
   */
  open fun getAll(
    limit: Int = 100,
    offset: Long = 0,
  ): Flow<T> =
    channelFlow {
      query(readOnly = true) {
        table
          .selectAll()
          .limit(limit)
          .offset(offset)
          .forEach { row ->
            send(row.decode())
          }
      }
    }

  /**
   * Persists or overwrites a resource using an upsert operation.
   *
   * @param idValue The unique identifier to associate with this entity.
   *
   * @param item The entity to save.
   */
  open suspend fun save(
    idValue: ID,
    item: T,
  ) = query {
    table.upsert {
      encode(it, item)
    }
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
