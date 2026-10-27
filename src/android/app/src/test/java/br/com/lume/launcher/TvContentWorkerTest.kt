package br.com.lume.launcher

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class TvContentWorkerTest {
    @Test fun stalledProviderCannotAccumulateThreadsOrQueuedQueriesAcrossRestarts() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val exited = CountDownLatch(1)
        val first = TvContentWorker.submit(Runnable {
            entered.countDown()
            try { while (release.count > 0) { try { release.await() } catch (_: InterruptedException) { } } }
            finally { exited.countDown() }
        })
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS)); first.cancel(true)
            assertEquals(1L, exited.count)
            repeat(5) {
                val queued = TvContentWorker.submit(Runnable { fail("Canceled query ran") })
                assertThrows(RejectedExecutionException::class.java) { TvContentWorker.submit(Runnable { }) }
                assertEquals(1, TvContentWorker.executor.queue.size)
                queued.cancel(true); TvContentWorker.purgeCanceled()
                assertTrue(TvContentWorker.executor.queue.isEmpty())
                assertEquals(1, TvContentWorker.executor.largestPoolSize)
            }
        } finally { release.countDown(); assertTrue(exited.await(2, TimeUnit.SECONDS)); TvContentWorker.purgeCanceled() }
        TvContentWorker.submit(Runnable { }).get(2, TimeUnit.SECONDS)
    }
}
