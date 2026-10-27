package br.com.lume.launcher

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TvContentCancellationTest {
    @Test fun blockedCancellationCannotBlockCallerOrIndependentDeadlineOrGrowItsQueue() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val exited = CountDownLatch(1)
        val queuedDone = CountDownLatch(1); val rejectedRan = AtomicBoolean(false)
        val caller = Executors.newSingleThreadExecutor()
        val timer = Executors.newSingleThreadScheduledExecutor()
        try {
            assertTrue(caller.submit<Boolean> {
                TvContentCancellation.tryCancel {
                    entered.countDown()
                    try { release.await() } finally { exited.countDown() }
                }
            }.get(2, TimeUnit.SECONDS))
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertTrue(caller.submit<Boolean> { TvContentCancellation.tryCancel { queuedDone.countDown() } }.get(2, TimeUnit.SECONDS))
            assertFalse(caller.submit<Boolean> { TvContentCancellation.tryCancel { rejectedRan.set(true) } }.get(2, TimeUnit.SECONDS))
            assertEquals(1, TvContentCancellation.executor.queue.size)
            assertEquals(1, TvContentCancellation.executor.largestPoolSize)

            val deadline = CountDownLatch(1)
            timer.schedule({ deadline.countDown() }, 0, TimeUnit.MILLISECONDS)
            assertTrue(deadline.await(2, TimeUnit.SECONDS))
            assertEquals(1L, exited.count)
            assertFalse(rejectedRan.get())
        } finally {
            release.countDown()
            caller.shutdownNow(); timer.shutdownNow()
            assertTrue(exited.await(2, TimeUnit.SECONDS))
            assertTrue(queuedDone.await(2, TimeUnit.SECONDS))
        }
        assertFalse(rejectedRan.get())
    }

    @Test fun cancellationFailureDoesNotPreventTheNextCancellation() {
        val failed = CountDownLatch(1); val recovered = CountDownLatch(1)
        assertTrue(TvContentCancellation.tryCancel { failed.countDown(); throw IllegalStateException("Cancellation failed") })
        assertTrue(failed.await(2, TimeUnit.SECONDS))
        assertTrue(TvContentCancellation.tryCancel { recovered.countDown() })
        assertTrue(recovered.await(2, TimeUnit.SECONDS))
    }
}
