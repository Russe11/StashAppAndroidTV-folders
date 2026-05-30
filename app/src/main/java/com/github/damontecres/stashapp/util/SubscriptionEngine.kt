package com.github.damontecres.stashapp.util

import android.util.Log
import com.apollographql.apollo.api.Subscription
import com.github.damontecres.stashapp.api.EntityChangedSubscription
import com.github.damontecres.stashapp.api.JobProgressSubscription
import com.github.damontecres.stashapp.util.realtime.EntityChange
import com.github.damontecres.stashapp.util.realtime.EntityOperation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Engine for handling graphql subscriptions
 *
 * @param server the stash server to connect to
 * @param client the client to use, defaults to one for the server
 * @param ioDispatcher the dispatcher to use for general I/O operations
 * @param callbackDispatcher the dispatcher to use for running callbacks from subscription results
 */
class SubscriptionEngine(
    server: StashServer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : StashEngine(server) {
    private suspend fun <D : Subscription.Data> executeSubscription(
        subscription: Subscription<D>,
        consumer: (D) -> Unit,
    ) = withContext(ioDispatcher) {
        val name = subscription.name()
        val id = OPERATION_ID.getAndIncrement()

        client.subscription(subscription).toFlow().collect { response ->
            if (response.data != null) {
                Log.v(TAG, "executeSubscription $id $name response received")
                withContext(callbackDispatcher) {
                    consumer.invoke(response.data!!)
                }
            } else if (response.exception != null) {
                throw createException(id, name, response.exception!!) { msg, ex ->
                    SubscriptionException(id, name, msg, ex)
                }
            } else {
                val errorMessages = response.errors!!.joinToString("\n") { it.message }
                Log.e(TAG, "Errors in $id $name: ${response.errors}")
                throw SubscriptionException(id, name, "Error in $name: $errorMessages")
            }
        }
        Log.v(TAG, "Completed subscription $id $name")
    }

    suspend fun subscribeToJobs(consumer: (JobProgressSubscription.Data) -> Unit) {
        val subscription = JobProgressSubscription()
        executeSubscription(subscription, consumer)
    }

    /**
     * Subscribe to the NG `entityChanged` live-refresh feed as a cold [Flow] of decoded,
     * Apollo-free [EntityChange]s. The flow runs on [ioDispatcher]; it completes when the WS
     * closes and throws a [SubscriptionException] on a transport/GraphQL error — callers
     * ([com.github.damontecres.stashapp.util.realtime.LiveRefreshRepository]) wrap it in a
     * reconnect loop.
     *
     * Gating on `serverCapabilities.features` is the caller's responsibility (the WS would
     * simply error on a server that doesn't expose the field); this engine just runs the op.
     *
     * @param types optional entity-kind filter (e.g. ["Scene", "Tag"]); null/empty streams all.
     */
    fun entityChanges(types: List<String>? = null): Flow<EntityChange> =
        flow {
            val subscription = EntityChangedSubscription(types = types)
            val name = subscription.name()
            val id = OPERATION_ID.getAndIncrement()
            client.subscription(subscription).toFlow().collect { response ->
                val data = response.data
                if (data != null) {
                    val e = data.entityChanged
                    emit(
                        EntityChange(
                            entity = e.entity,
                            id = e.id,
                            operation = EntityOperation.fromServer(e.operation),
                        ),
                    )
                } else if (response.exception != null) {
                    throw createException(id, name, response.exception!!) { msg, ex ->
                        SubscriptionException(id, name, msg, ex)
                    }
                } else {
                    val errorMessages = response.errors?.joinToString("\n") { it.message }.orEmpty()
                    Log.e(TAG, "Errors in $id $name: ${response.errors}")
                    throw SubscriptionException(id, name, "Error in $name: $errorMessages")
                }
            }
            Log.v(TAG, "Completed subscription $id $name")
        }.flowOn(ioDispatcher)

    companion object {
        private const val TAG = "SubscriptionEngine"
        private val OPERATION_ID = AtomicInteger(0)
    }

    open class SubscriptionException(
        id: Int,
        mutationName: String,
        msg: String? = null,
        cause: Exception? = null,
    ) : ServerCommunicationException(id, mutationName, msg, cause)
}
