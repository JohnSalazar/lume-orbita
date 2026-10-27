package br.com.lume.launcher

import br.com.lume.launcher.core.ScreensaverAttemptCoordinator
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MemoryImageWorkerTest {
    @Test fun stalledCanceledWorkerAndRepeatedRendererRestartsKeepOneWorkerAndOneFiniteQueue() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val unexpectedRuns = AtomicInteger()
        val firstOwner = ScreensaverAttemptCoordinator().apply { start() }
        val firstTicket = firstOwner.begin()!!
        val blocked = MemoryImageWorker.submit(Runnable {
            entered.countDown()
            try {
                while (release.count > 0) {
                    try { release.await() } catch (_: InterruptedException) { /* Simulate DNS ignoring cancel. */ }
                }
            } finally { exited.countDown() }
        })
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertTrue(firstOwner.complete(firstTicket)); firstOwner.stop()
            blocked.cancel(true)
            assertTrue(blocked.isDone)
            assertEquals(1L, exited.count) // Future.done is not physical worker completion.
            repeat(8) {
                val nextOwner = ScreensaverAttemptCoordinator().apply { start() }
                val ticket = nextOwner.begin()!!
                val queued = MemoryImageWorker.submit(Runnable { unexpectedRuns.incrementAndGet() })
                assertEquals(1, MemoryImageWorker.executor.queue.size)
                assertThrows(RejectedExecutionException::class.java) {
                    MemoryImageWorker.submit(Runnable { unexpectedRuns.incrementAndGet() })
                }
                assertTrue(nextOwner.complete(ticket)); nextOwner.stop()
                queued.cancel(true); MemoryImageWorker.purgeCanceled()
                assertTrue(MemoryImageWorker.executor.queue.isEmpty())
                assertTrue(MemoryImageWorker.executor.largestPoolSize <= 1)
                assertEquals(0, unexpectedRuns.get())
            }
        } finally {
            release.countDown()
            assertTrue(exited.await(2, TimeUnit.SECONDS))
            MemoryImageWorker.purgeCanceled()
        }
        // Prove the shared worker recovers after the old transport eventually returns.
        val recovered = MemoryImageWorker.submit(Runnable { unexpectedRuns.incrementAndGet() })
        recovered.get(2, TimeUnit.SECONDS)
        assertEquals(1, unexpectedRuns.get())
    }
}
