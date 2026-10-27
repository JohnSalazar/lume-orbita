package br.com.lume.launcher

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class ProgramArtworkWorkerTest {
    @Test fun stalledArtworkRequestsStayWithinTwoWorkersAndFourQueuedRequests() {
        val entered = CountDownLatch(2); val release = CountDownLatch(1); val exited = CountDownLatch(2)
        val blocked = (0..1).map {
            ProgramArtworkWorker.submit(Runnable {
                entered.countDown()
                try { while (release.count > 0) { try { release.await() } catch (_: InterruptedException) { } } }
                finally { exited.countDown() }
            })
        }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS)); blocked.forEach { it.cancel(true) }
            assertEquals(2L, exited.count)
            val queued = (0..3).map { ProgramArtworkWorker.submit(Runnable { fail("Canceled task ran") }) }
            assertEquals(4, ProgramArtworkWorker.executor.queue.size)
            assertThrows(RejectedExecutionException::class.java) { ProgramArtworkWorker.submit(Runnable { }) }
            queued.forEach { it.cancel(true) }; ProgramArtworkWorker.purgeCanceled()
            assertTrue(ProgramArtworkWorker.executor.queue.isEmpty())
            assertEquals(2, ProgramArtworkWorker.executor.largestPoolSize)
        } finally { release.countDown(); assertTrue(exited.await(2, TimeUnit.SECONDS)); ProgramArtworkWorker.purgeCanceled() }
        ProgramArtworkWorker.submit(Runnable { }).get(2, TimeUnit.SECONDS)
    }
}
