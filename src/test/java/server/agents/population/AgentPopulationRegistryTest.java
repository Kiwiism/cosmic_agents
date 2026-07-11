package server.agents.population;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentPopulationRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void missingStoreStartsDisabledAndPersistsStableRoster() throws IOException {
        Path file = tempDir.resolve("population.json");
        AgentPopulationRegistry registry = new AgentPopulationRegistry(new FileAgentPopulationStore(file));

        assertFalse(registry.snapshot().enabled());
        assertEquals(1.0, registry.snapshot().multiplier());
        assertTrue(registry.add(new AgentPopulationRecord(2, "Zulu", null)));
        assertTrue(registry.add(new AgentPopulationRecord(1, "alpha", 7)));
        assertFalse(registry.add(new AgentPopulationRecord(3, "ALPHA", null)));
        registry.setEnabled(true);
        registry.setMultiplier(0.5);

        AgentPopulationSnapshot reloaded = new FileAgentPopulationStore(file).load();
        assertTrue(reloaded.enabled());
        assertEquals(0.5, reloaded.multiplier());
        assertEquals(java.util.List.of("alpha", "Zulu"),
                reloaded.agents().stream().map(AgentPopulationRecord::name).toList());
    }

    @Test
    void rosterMutationsAreExplicitAndPersistent() throws IOException {
        Path file = tempDir.resolve("population.json");
        AgentPopulationRegistry registry = new AgentPopulationRegistry(new FileAgentPopulationStore(file));
        registry.add(new AgentPopulationRecord(10, "agent10", null));

        assertTrue(registry.setCrew("AGENT10", 4));
        assertEquals(4, registry.snapshot().agents().getFirst().crewId());
        assertFalse(registry.setCrew("missing", 1));
        assertTrue(registry.remove("Agent10"));
        assertFalse(registry.remove("Agent10"));
        assertTrue(new FileAgentPopulationStore(file).load().agents().isEmpty());
    }

    @Test
    void failedSaveDoesNotPublishPartialState() throws IOException {
        AgentPopulationStore failingStore = new AgentPopulationStore() {
            @Override
            public AgentPopulationSnapshot load() {
                return AgentPopulationSnapshot.DISABLED;
            }

            @Override
            public void save(AgentPopulationSnapshot snapshot) throws IOException {
                throw new IOException("disk full");
            }
        };
        AgentPopulationRegistry registry = new AgentPopulationRegistry(failingStore);

        assertThrows(IOException.class,
                () -> registry.add(new AgentPopulationRecord(1, "agent", null)));
        assertTrue(registry.snapshot().agents().isEmpty());
    }
}
