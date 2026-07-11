package server.agents.population;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentPopulationSessionServiceTest {
    @Test
    void rejectsIneligibleCharacterBeforeSpawn() throws Exception {
        AtomicInteger spawns = new AtomicInteger();
        AgentPopulationSessionService service = new AgentPopulationSessionService(backend(false, spawns));

        assertFalse(service.start(new AgentPopulationRecord(1, "player", null)));
        assertEquals(0, spawns.get());
    }

    @Test
    void concurrentStartsAllowOnlyOneBackendTransition() throws Exception {
        AtomicInteger spawns = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AgentPopulationSessionService service = new AgentPopulationSessionService(new AgentPopulationSessionService.Backend() {
            @Override public boolean isEligibleAgent(int characterId) { return true; }
            @Override public boolean isLive(int characterId) { return false; }
            @Override public boolean spawnSelfDirected(AgentPopulationRecord record) throws Exception {
                spawns.incrementAndGet(); entered.countDown(); release.await(); return true;
            }
            @Override public boolean stop(int characterId) { return false; }
        });
        AgentPopulationRecord record = new AgentPopulationRecord(1, "agent", null);
        Thread first = new Thread(() -> assertDoesNotThrow(() -> service.start(record)));
        first.start();
        entered.await();

        assertFalse(service.start(record));
        release.countDown();
        first.join();
        assertEquals(1, spawns.get());
        assertEquals(0, service.transitionsInProgress());
    }

    private static AgentPopulationSessionService.Backend backend(boolean eligible, AtomicInteger spawns) {
        return new AgentPopulationSessionService.Backend() {
            @Override public boolean isEligibleAgent(int characterId) { return eligible; }
            @Override public boolean isLive(int characterId) { return false; }
            @Override public boolean spawnSelfDirected(AgentPopulationRecord record) { spawns.incrementAndGet(); return true; }
            @Override public boolean stop(int characterId) { return false; }
        };
    }
}
