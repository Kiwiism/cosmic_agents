package scripting.portal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalScriptManagerTest {
    @Test
    void serializesConcurrentEntriesIntoOneScriptContext() throws Exception {
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maximumConcurrentCalls = new AtomicInteger();
        PortalScript script = PortalScriptManager.serialized(ppi -> {
            int active = activeCalls.incrementAndGet();
            maximumConcurrentCalls.accumulateAndGet(active, Math::max);
            try {
                Thread.sleep(10L);
                return true;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                activeCalls.decrementAndGet();
            }
        });

        int callers = 12;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < callers; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return script.enter(null);
                }));
            }
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, maximumConcurrentCalls.get());
    }
}
