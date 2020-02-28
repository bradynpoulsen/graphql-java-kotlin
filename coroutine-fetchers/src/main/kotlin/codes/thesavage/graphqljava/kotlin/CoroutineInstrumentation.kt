package codes.thesavage.graphqljava.kotlin

import codes.thesavage.graphqljava.kotlin.CoroutineInstrumentation.ExecutionPrep
import graphql.ExecutionInput
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionId
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.schema.DataFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Instrumentation that provides control over the context for [SuspendingDataFetcher] coroutines (including the dispatcher).
 *
 * ### Basic Usage
 *
 * ```kotlin
 * GraphQL.newGraphQL(schema)
 *     .instrumentation(CoroutineInstrumentation(Dispatchers.IO + MyCoroutineElement("Hello, world!")))
 *     .build();
 *
 * SuspendingDataFetcher { environment ->
 *     // running inside of Dispatchers.IO
 *     println(coroutineContext[MyCoroutineElement]) // prints 'MyCoroutineElement(value = "Hello, world!")'
 * }
 * ```
 *
 * ### Structured Concurrency
 *
 * Coroutines for the same GraphQL request are tracked to the same [Job][kotlinx.coroutines.Job] hierarchy. This allows
 * one failing [SuspendingDataFetcher] to properly cancel other [fetchers][SuspendingDataFetcher] for the same request.
 *
 * ```kotlin
 * // Two separate SuspendingDataFetcher's that are execute for a given request
 *
 * SuspendingDataFetcher {
 *     throw SomethingBadException()
 * }
 *
 * SuspendingDataFetcher {
 *     delay(100) // cancelled because the other data fetcher throws an uncaught exception
 * }
 * ```
 *
 * @constructor Creates a new instrumentation with the provided scope and [ExecutionPrep] configuration.
 * @param scope The [CoroutineScope] for all coroutines to be spawned from
 * @param prepareExecution [ExecutionPrep] called when a new execution is beginning to allow [CoroutineScope] overrides
 */
@UseExperimental(ExperimentalExecutionPrep::class)
class CoroutineInstrumentation @ExperimentalExecutionPrep constructor(
    scope: CoroutineScope,
    private val prepareExecution: ExecutionPrep.() -> Unit
) : SimpleInstrumentation() {
    /**
     * Creates a new instrumentation with the provided [scope]
     * @param scope The [CoroutineScope] for all coroutines to be spawned from
     */
    constructor(scope: CoroutineScope) : this(scope, {})

    private val instrumentJob = SupervisorJob(parent = scope.coroutineContext[Job])
    private val instrumentScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = scope.coroutineContext + instrumentJob
    }

    /**
     * Cancels all coroutines spawned by this instrumentation with a [InstrumentationCancellationException].
     */
    fun cancel() {
        instrumentJob.cancel(InstrumentationCancellationException())
    }

    /**
     * Cancels all coroutines spawned by this instrumentation with a [InstrumentationCancellationException] and suspends
     * until they have completed.
     *
     * _Note: overriding the parent job via [ExecutionPrep] will cause the instrumentation to join before jobs might have
     * completed._
     */
    suspend fun cancelAndJoin() {
        instrumentJob.cancel(InstrumentationCancellationException())
        instrumentJob.join()
    }

    /**
     * Initializes the [ExecutionScope] that coroutines are launched against.
     */
    override fun createState(): InstrumentationState = ExecutionScope()

    /**
     * Configures [SuspendingDataFetcher]'s with the current request's [ExecutionScope].
     */
    override fun instrumentDataFetcher(
        dataFetcher: DataFetcher<*>,
        parameters: InstrumentationFieldFetchParameters
    ) = when (dataFetcher) {
        is SuspendingDataFetcher<*> -> dataFetcher viaScope parameters.scope
        else -> dataFetcher
    }

    /**
     * Applies any [ExecutionPrep] rules that were evaluated.
     */
    @ExperimentalExecutionPrep
    override fun instrumentExecutionContext(
        executionContext: ExecutionContext,
        parameters: InstrumentationExecutionParameters
    ): ExecutionContext {
        val preparation = ExecutionPrep(executionContext).apply(prepareExecution)
        return preparation.overrideScope?.let { overrideScope ->
            executionContext.transform {
                it.instrumentationState(executionContext.scope.merge(overrideScope, preparation.overrideParentJob))
            }
        } ?: executionContext
    }

    /**
     * Configuration object for overriding the [CoroutineScope] for the current request.
     */
    @ExperimentalExecutionPrep
    class ExecutionPrep internal constructor(private val executionContext: ExecutionContext) {
        /**
         * Get the context that was configured on the [ExecutionInput].
         */
        fun <T> context() = executionContext.getContext<T>()

        /**
         * Get the [ExecutionId] for the current request.
         */
        val executionId get() = executionContext.executionId

        /**
         * Get the request variables.
         */
        val variables get() = executionContext.variables

        /**
         * [CoroutineScope] containing context overrides for this request's coroutines.
         *
         * While many cases may be satisfied with the shared [CoroutineScope] for all requests, there may be times when you need
         * access to modify the scope that coroutines based on execution.
         *
         * ```
         * val instrumentation = CoroutineInstrumentation(applicationScope) {
         *     // Set a new dispatcher that's configured as the ExecutionInput's local context
         *     overrideScope = CoroutineScope(context<CoroutineDispatcher?>() ?: EmptyCoroutineContext)
         * }
         *
         * val input = ExecutionInput.newExecutionInput(query)
         *     // use the flexibility of the input context to specify per-request dispatchers
         *     .context(Executors.newFixedThreadPool(10).asCoroutineDispatcher())
         *     .build()
         *
         * graphQL.executeAsync(input)
         * ```
         */
        var overrideScope: CoroutineScope? = null

        /**
         * If `true`, overrides the request's parent job with one inside the [overrideScope].
         *
         * By default, providing scope overrides does not manipulate the [Job] that coroutines are launched under. To override
         * the [Job] with one contained in your overriding scope, you can set `overrideParentJob`.
         *
         * ```
         * val instrumentation = CoroutineInstrumentation(applicationScope) {
         *     overrideScope = CoroutineScope(context<Job?>() ?: EmptyCoroutineContext)
         *     // override the parent job, if one is present in the context
         *     overrideParentJob = true
         * }
         *
         * fun KtorApplication.myModule() {
         *     routing {
         *         get("/graphql") {
         *             val input = ExecutionInput.newExecutionInput(request.receiveQuery())
         *                 // link this execution's coroutines to the Ktor call lifecycle
         *                 .context(coroutineContext[Job])
         *                 .build()
         *
         *             graphql.executeAsync(input).await()
         *             ...
         *         }
         *     }
         * }
         * ```
         */
        var overrideParentJob = false
    }

    private inner class ExecutionScope private constructor(
        private val scope: CoroutineScope,
        private val job: Job
    ) : InstrumentationState, CoroutineScope {
        constructor() : this(instrumentScope, Job(parent = instrumentJob))

        override val coroutineContext: CoroutineContext get() = scope.coroutineContext + job

        /**
         * Merges this [ExecutionScope] with another [CoroutineScope].
         */
        @ExperimentalExecutionPrep
        fun merge(overridingScope: CoroutineScope, overrideParentJob: Boolean): ExecutionScope {
            val newScope = object : CoroutineScope {
                override val coroutineContext: CoroutineContext
                    get() = scope.coroutineContext + overridingScope.coroutineContext
            }

            /**
             * Configure a new [Job] if a overriding parent is provided for this [ExecutionPrep].
             */
            val newJob = if (overrideParentJob && overridingScope.coroutineContext[Job] != null) {
                /**
                 * Cancel the original [ExecutionScope.job] to prevent coroutine leaks even though no child jobs
                 * should have been launched, yet.
                 */
                job.cancel()
                /**
                 * Create the new [ExecutionScope.job] using the [overridingScope]'s [Job] as the parent.
                 */
                Job(parent = newScope.coroutineContext[Job]).also { newJob ->
                    /**
                     * We register a listener on the [CoroutineInstrumentation.instrumentJob] to cancel the new
                     * [ExecutionScope.job] if the instrumentation was cancelled.
                     */
                    val parentCancelling = instrumentJob.invokeOnCompletion {
                        if (it is CancellationException) {
                            newJob.cancel(it)
                        }
                    }
                    /**
                     * When the GraphQL request completes, we detach our listener on the instrumentation's job.
                     */
                    newJob.invokeOnCompletion {
                        parentCancelling.dispose()
                    }
                }
            } else job
            return ExecutionScope(newScope, newJob)
        }
    }

    private class InstrumentationCancellationException :
        CancellationException("CoroutineInstrumentation has been closed")

    private inline val InstrumentationFieldFetchParameters.scope get() = getInstrumentationState<ExecutionScope>()
    private inline val ExecutionContext.scope get() = instrumentationState as ExecutionScope
}

/**
 * APIs that are part of the experimental [ExecutionPrep] feature. These APIs are not finalized and are subject to
 * change in future releases.
 * @see ExecutionPrep
 */
@Experimental
annotation class ExperimentalExecutionPrep

/**
 * Creates a [CoroutineInstrumentation] using the enclosing [CoroutineScope].
 */
@Suppress("FunctionName")
fun CoroutineScope.CoroutineInstrumentation() = CoroutineInstrumentation(this)

/**
 * Creates a [CoroutineInstrumentation] using the enclosing [CoroutineScope] and provided [ExecutionPrep] configuration.
 */
@Suppress("FunctionName")
@ExperimentalExecutionPrep
fun CoroutineScope.CoroutineInstrumentation(
    prepareRequest: ExecutionPrep.() -> Unit
) = CoroutineInstrumentation(this, prepareRequest)
