package com.tozes.hermescompanion.core.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Android's GATT client runs one operation at a time: a second one started
 * before the first one's callback is dropped. This queue starts each
 * operation only after the previous one finished, and waits for its callback,
 * up to a timeout.
 */
class GattOperationQueue {
    enum class Operation { CONNECT, MTU, DISCOVER, SUBSCRIBE, WRITE }

    /** An operation that couldn't start, timed out, or was abandoned by [failAll]. */
    class OperationFailed(val operation: Operation, val timedOut: Boolean, message: String) : Exception("$operation: $message")

    private class Pending(val operation: Operation, val result: CompletableDeferred<Int>)

    private val mutex = Mutex()
    private var pending: Pending? = null

    /** The operation waiting for its callback, if any. */
    val current: Operation? get() = pending?.operation

    /**
     * Runs [start] once the previous operation has finished, then waits for
     * [complete] with the same [operation]; returns the callback's status.
     *
     * @throws OperationFailed when [start] returns false, after [timeout], or on [failAll].
     */
    suspend fun perform(operation: Operation, timeout: Duration, start: () -> Boolean): Int = mutex.withLock {
        val result = CompletableDeferred<Int>()
        pending = Pending(operation, result)
        try {
            if (!start()) throw OperationFailed(operation, timedOut = false, "didn't start")
            withTimeoutOrNull(timeout) { result.await() } ?: throw OperationFailed(operation, timedOut = true, "timed out after $timeout")
        } finally {
            pending = null
        }
    }

    /** Reports a callback; returns false when no [operation] is waiting for it. */
    fun complete(operation: Operation, status: Int): Boolean {
        val waiting = pending ?: return false
        if (waiting.operation != operation) return false
        return waiting.result.complete(status)
    }

    /** Abandons the waiting operation, e.g. when the link drops. */
    fun failAll(reason: String) {
        pending?.let { it.result.completeExceptionally(OperationFailed(it.operation, timedOut = false, reason)) }
    }
}
