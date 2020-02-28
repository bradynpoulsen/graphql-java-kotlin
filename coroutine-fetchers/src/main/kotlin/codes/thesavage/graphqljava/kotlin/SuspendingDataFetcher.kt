package codes.thesavage.graphqljava.kotlin

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

/**
 * A suspendable [DataFetcher] that enables the use of [kotlin.coroutines].
 *
 * ```kotlin
 * class UserByUsernameFetcher(private val service: UsersService) : SuspendingDataFetcher<User?> {
 *     override suspend fun fetch(environment: DataFetchingEnvironment) = service.findByUsername(environment.getArgument("username"))
 * }
 * ```
 *
 * All implementations SHOULD follow the rules of Structured Concurrency to ensure that no coroutines or resources are
 * leaked by regular or exceptional returning of the [fetch] implementation.
 *
 * Control over the [CoroutineContext][kotlin.coroutines.CoroutineContext] that a graph's coroutines are launched can
 * be managed through [CoroutineInstrumentation].
 */
interface SuspendingDataFetcher<T> : DataFetcher<CompletableFuture<T>> {
    /**
     * Routine to fetch a graphql value. The provided [DataFetchingEnvironment][https://www.graphql-java.com/documentation/v14/data-fetching/]
     * provides context to how the value should be resolved in the graphql request.
     * @return The found value that is optionally wrapped in a [graphql.execution.DataFetcherResult].
     */
    suspend fun fetch(environment: DataFetchingEnvironment): T

    /**
     * Binary-compatible [DataFetcher.get] support launched inside the [GlobalScope] used when [CoroutineInstrumentation]
     * is not configured.
     */
    @Deprecated("Do not use get method directly", level = DeprecationLevel.HIDDEN)
    @UseExperimental(ExperimentalCoroutinesApi::class)
    override fun get(environment: DataFetchingEnvironment): CompletableFuture<T> =
        GlobalScope.future(start = CoroutineStart.UNDISPATCHED) {
            fetch(environment)
        }
}

/**
 * Factory method for creating a [SuspendingDataFetcher] that invokes the provided [fetch] function.
 *
 * ```kotlin
 * val currentTimeFetcher = SuspendingDataFetcher {
 *     ntpService.currentTime()
 * }
 * ```
 */
@Suppress("FunctionName")
fun <T> SuspendingDataFetcher(
    fetch: suspend (environment: DataFetchingEnvironment) -> T
) = object : SuspendingDataFetcher<T> {
    override suspend fun fetch(environment: DataFetchingEnvironment) = fetch(environment)
}

internal infix fun <T> SuspendingDataFetcher<T>.viaScope(scope: CoroutineScope): DataFetcher<CompletableFuture<T>> =
    DataFetcher {
        scope.future { fetch(it) }
    }
