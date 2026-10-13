package com.tozes.hermescompanion.core.ble

import com.tozes.hermescompanion.core.ble.GattOperationQueue.Operation
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class GattOperationQueueTest {
    private val queue = GattOperationQueue()

    @Test
    fun theNextOperationStartsAfterTheLastOneFinished() = runTest {
        val started = mutableListOf<Operation>()
        val mtu = async { queue.perform(Operation.MTU, 5.seconds) { started += Operation.MTU; true } }
        val discover = async { queue.perform(Operation.DISCOVER, 5.seconds) { started += Operation.DISCOVER; true } }
        runCurrent()
        assertEquals(listOf(Operation.MTU), started)
        assertEquals(Operation.MTU, queue.current)
        assertTrue(queue.complete(Operation.MTU, 0))
        assertEquals(0, mtu.await())
        runCurrent()
        assertEquals(listOf(Operation.MTU, Operation.DISCOVER), started)
        assertTrue(queue.complete(Operation.DISCOVER, 129))
        assertEquals(129, discover.await())
    }

    @Test
    fun aCallbackForAnotherOperationIsIgnored() = runTest {
        val write = async { queue.perform(Operation.WRITE, 5.seconds) { true } }
        runCurrent()
        assertFalse(queue.complete(Operation.SUBSCRIBE, 0))
        assertTrue(queue.complete(Operation.WRITE, 0))
        assertEquals(0, write.await())
        assertFalse(queue.complete(Operation.WRITE, 0))
    }

    @Test
    fun anOperationThatDoesNotStartFails() = runTest {
        val failure = runCatching { queue.perform(Operation.DISCOVER, 5.seconds) { false } }.exceptionOrNull()
        assertTrue(failure is GattOperationQueue.OperationFailed && !failure.timedOut)
        assertEquals(null, queue.current)
    }

    @Test
    fun anOperationWithoutACallbackTimesOutAndFreesTheQueue() = runTest {
        val stuck = async { runCatching { queue.perform(Operation.MTU, 5.seconds) { true } } }
        val next = async { queue.perform(Operation.DISCOVER, 5.seconds) { true } }
        advanceTimeBy(5.1.seconds)
        val failure = stuck.await().exceptionOrNull()
        assertTrue(failure is GattOperationQueue.OperationFailed && failure.timedOut)
        runCurrent()
        assertEquals(Operation.DISCOVER, queue.current)
        queue.complete(Operation.DISCOVER, 0)
        assertEquals(0, next.await())
    }

    @Test
    fun failAllAbandonsTheWaitingOperation() = runTest {
        val write = async { runCatching { queue.perform(Operation.WRITE, 5.seconds) { true } } }
        runCurrent()
        queue.failAll("disconnected")
        assertTrue(write.await().exceptionOrNull() is GattOperationQueue.OperationFailed)
    }
}
