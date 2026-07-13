package server.agents.plans.amherst;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.async.AgentAsyncTaskGateway;
import server.agents.testing.MutablePrimitiveGatewayFixture;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmherstPlanAsyncPersistenceTest {
    @AfterEach
    void tearDown() {
        System.clearProperty("agents.scheduler.mode");
        AgentAsyncTaskGateway.runtime().clearSession(77);
        AgentRuntimeRegistry.clear();
    }

    @Test
    void centralModeLoadsProgressOffThreadAndAppliesThroughMailbox() throws Exception {
        System.setProperty("agents.scheduler.mode", "central-sequential");
        MutablePrimitiveGatewayFixture fixture = new MutablePrimitiveGatewayFixture();
        AgentRuntimeRegistry.registerEntry(1, fixture.entry);
        BlockingStore store = new BlockingStore();
        AmherstPlanRuntimeRunner runner = new AmherstPlanRuntimeRunner(
                card(), store, new AmherstPlanProgressService(),
                new AmherstObjectiveReconciler(fixture.gateway),
                new AmherstObjectiveHandlerRegistry(fixture.gateway));

        runner.start(fixture.entry, fixture.agent, 1L);

        assertTrue(store.loadStarted.await(5, TimeUnit.SECONDS));
        assertNull(fixture.entry.amherstPlanExecutionState().progress());
        assertTrue(fixture.entry.amherstPlanExecutionState().active());
        store.allowLoad.countDown();
        awaitMailbox(fixture);
        fixture.entry.actionMailbox().drain(fixture.entry, 8);

        assertNotNull(fixture.entry.amherstPlanExecutionState().progress());
        assertTrue(!fixture.entry.amherstPlanExecutionState().loading);
    }

    private static void awaitMailbox(MutablePrimitiveGatewayFixture fixture) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (fixture.entry.actionMailbox().size() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertTrue(fixture.entry.actionMailbox().size() > 0, "load completion did not reach mailbox");
    }

    private static AmherstPlanCard card() {
        AmherstPlanObjective stop = new AmherstPlanObjective(
                "stop", AmherstPlanObjectiveKind.STOP_PLAN, 0, 0, 10000,
                null, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null,
                "done");
        return new AmherstPlanCard(
                1, "async-persistence-test", "test", "test", "high", "test", "ordered",
                new AmherstPlanCard.FocusPolicy("locked", false, Set.of(), "always"),
                new AmherstPlanCard.EntryCriteria(10000, "maple-island", "clean"),
                new AmherstPlanCard.ExitCriteria("all", 10000, Set.of(), Set.of(), Set.of()),
                Set.of(), Set.of(), List.of(stop));
    }

    private static final class BlockingStore implements AmherstPlanProgressStore {
        private final CountDownLatch loadStarted = new CountDownLatch(1);
        private final CountDownLatch allowLoad = new CountDownLatch(1);
        private volatile AmherstPlanProgressSnapshot snapshot;

        @Override
        public AmherstPlanProgressSnapshot load(String planId, int characterId) throws IOException {
            loadStarted.countDown();
            try {
                if (!allowLoad.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("test load timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
            return snapshot == null ? AmherstPlanProgressSnapshot.empty(planId, characterId) : snapshot;
        }

        @Override
        public void save(AmherstPlanProgressSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public void delete(String planId, int characterId) {
            snapshot = null;
        }
    }
}
