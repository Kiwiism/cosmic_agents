package server.agents.runtime.async;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAsyncExecutorRegistryTest {
    private final AgentAsyncExecutorRegistry registry = new AgentAsyncExecutorRegistry();

    @AfterEach
    void tearDown() {
        registry.close();
        System.clearProperty("agents.async.navigation.threads");
        System.clearProperty("agents.async.navigation.queueCapacity");
        System.clearProperty("agents.async.llm.threads");
        System.clearProperty("agents.async.llm.queueCapacity");
    }

    @Test
    void workloadKindsOwnIndependentBoundedLanes() throws Exception {
        System.setProperty("agents.async.navigation.threads", "1");
        System.setProperty("agents.async.navigation.queueCapacity", "2");
        System.setProperty("agents.async.llm.threads", "1");
        System.setProperty("agents.async.llm.queueCapacity", "3");
        CountDownLatch navigationStarted = new CountDownLatch(1);
        CountDownLatch llmStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        registry.execute(AgentAsyncWorkKind.NAVIGATION_GRAPH,
                () -> await(navigationStarted, release));
        registry.execute(AgentAsyncWorkKind.LLM_NETWORK,
                () -> await(llmStarted, release));

        assertTrue(navigationStarted.await(5, TimeUnit.SECONDS));
        assertTrue(llmStarted.await(5, TimeUnit.SECONDS));
        assertEquals(2, registry.queueCapacity(AgentAsyncWorkKind.NAVIGATION_GRAPH));
        assertEquals(3, registry.queueCapacity(AgentAsyncWorkKind.LLM_NETWORK));
        assertEquals(1, registry.activeCount(AgentAsyncWorkKind.NAVIGATION_GRAPH));
        assertEquals(1, registry.activeCount(AgentAsyncWorkKind.LLM_NETWORK));

        release.countDown();
        assertTrue(registry.shutdownAndAwait(
                AgentAsyncWorkKind.NAVIGATION_GRAPH, 5, TimeUnit.SECONDS));
        assertTrue(registry.shutdownAndAwait(
                AgentAsyncWorkKind.LLM_NETWORK, 5, TimeUnit.SECONDS));
        assertFalse(registry.isRunning(AgentAsyncWorkKind.NAVIGATION_GRAPH));
        assertFalse(registry.isRunning(AgentAsyncWorkKind.LLM_NETWORK));
    }

    private static void await(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            release.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
