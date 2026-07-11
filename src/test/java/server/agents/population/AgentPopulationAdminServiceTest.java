package server.agents.population;

import org.junit.jupiter.api.Test;
import server.agents.registry.AgentResolvedCharacter;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentPopulationAdminServiceTest {
    @Test
    void addRequiresLifecycleEligibility() throws IOException {
        Fixture fixture = new Fixture(false);

        String message = fixture.admin.add("ordinaryPlayer");

        assertTrue(message.contains("not an Agent-only"));
        assertTrue(fixture.registry.snapshot().agents().isEmpty());
    }

    @Test
    void confirmedWipeOnlyStopsSessionsAndClearsExternalRoster() throws IOException {
        Fixture fixture = new Fixture(true);
        fixture.registry.add(new AgentPopulationRecord(1, "agent", null));
        fixture.backend.live = true;

        AgentPopulationAdminService.WipeResult result = fixture.admin.wipeConfirm();

        assertEquals(1, result.removed());
        assertEquals(1, result.stopped());
        assertTrue(fixture.registry.snapshot().agents().isEmpty());
        assertTrue(result.messages().stream().anyMatch(line -> line.contains("were not deleted")));
    }

    @Test
    void confirmedWipeRetainsAgentWhoseLiveSessionCannotStop() throws IOException {
        Fixture fixture = new Fixture(true);
        fixture.registry.add(new AgentPopulationRecord(1, "agent", null));
        fixture.backend.live = true;
        fixture.backend.stopSucceeds = false;

        AgentPopulationAdminService.WipeResult result = fixture.admin.wipeConfirm();

        assertEquals(0, result.removed());
        assertEquals(1, fixture.registry.snapshot().agents().size());
        assertTrue(result.messages().stream().anyMatch(line -> line.contains("retained agent")));
    }

    private static final class Fixture {
        final MemoryStore store = new MemoryStore();
        final AgentPopulationRegistry registry;
        final Backend backend;
        final AgentPopulationAdminService admin;

        Fixture(boolean eligible) throws IOException {
            registry = new AgentPopulationRegistry(store);
            backend = new Backend(eligible);
            AgentPopulationSessionService sessions = new AgentPopulationSessionService(backend);
            AgentPopulationMetrics metrics = new AgentPopulationMetrics();
            AgentPopulationReconciler reconciler = new AgentPopulationReconciler(registry,
                    new AgentPopulationCurve(), new AgentPopulationPolicy(), sessions, metrics);
            AgentPopulationScheduler scheduler = new AgentPopulationScheduler(reconciler, 60_000);
            admin = new AgentPopulationAdminService(registry, sessions, scheduler, metrics,
                    name -> new AgentResolvedCharacter(1, name, 10, null));
        }
    }

    private static final class Backend implements AgentPopulationSessionService.Backend {
        final boolean eligible;
        boolean live;
        boolean stopSucceeds = true;
        Backend(boolean eligible) { this.eligible = eligible; }
        @Override public boolean isEligibleAgent(int characterId) { return eligible; }
        @Override public boolean isLive(int characterId) { return live; }
        @Override public boolean spawnSelfDirected(AgentPopulationRecord record) { live = true; return true; }
        @Override public boolean stop(int characterId) {
            if (!stopSucceeds) return false;
            boolean wasLive = live; live = false; return wasLive;
        }
    }

    private static final class MemoryStore implements AgentPopulationStore {
        AgentPopulationSnapshot snapshot = AgentPopulationSnapshot.DISABLED;
        @Override public AgentPopulationSnapshot load() { return snapshot; }
        @Override public void save(AgentPopulationSnapshot snapshot) { this.snapshot = snapshot; }
    }
}
