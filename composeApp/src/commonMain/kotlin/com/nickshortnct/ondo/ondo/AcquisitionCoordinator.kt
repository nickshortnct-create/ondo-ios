package com.nickshortnct.ondo.ondo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// The caller only waits for an acquisition. Its screen/row lifecycle must
// never own a request that may already have been accepted by the server.
internal class AcquisitionCoordinator(private val scope: CoroutineScope) {
    private data class Work(val pendingKey: String, val result: Deferred<Result<String>>)
    private val mutex = Mutex()
    private val work = mutableMapOf<String, Work>()
    private val _pendingItems = MutableStateFlow<Set<String>>(emptySet())
    val pendingItems: StateFlow<Set<String>> = _pendingItems

    suspend fun submit(requestId: String, pendingKey: String, request: suspend () -> String): String {
        val result = mutex.withLock {
            work[requestId]?.result ?: scope.async(start = CoroutineStart.LAZY) {
                try { Result.success(request()) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { Result.failure(e) }
            }.also { deferred ->
                work[requestId] = Work(pendingKey, deferred)
                _pendingItems.value = work.values.map { it.pendingKey }.toSet()
                // A completion handler also covers cancellation before the
                // lazy coroutine begins; a body-finally alone cannot do that.
                deferred.invokeOnCompletion {
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        withContext(NonCancellable) {
                            mutex.withLock {
                                if (work[requestId]?.result === deferred) work.remove(requestId)
                                _pendingItems.value = work.values.map { it.pendingKey }.toSet()
                            }
                        }
                    }
                }
                deferred.start()
            }
        }
        return result.await().getOrThrow()
    }
}
