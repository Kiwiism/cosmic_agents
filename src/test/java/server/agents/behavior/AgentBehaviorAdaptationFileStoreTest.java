package server.agents.behavior;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBehaviorAdaptationFileStoreTest {
    @TempDir Path directory;

    @Test
    void roundTripsIndependentEnergyCheckpoint() {
        AgentBehaviorAdaptationFileStore store =
                new AgentBehaviorAdaptationFileStore(directory);
        AgentBehaviorAdaptationSnapshot snapshot = new AgentBehaviorAdaptationSnapshot(
                42, 61, 8, 57, 0, 1_000L);

        assertTrue(store.load(27).isEmpty());
        store.save(27, snapshot);

        assertEquals(snapshot, store.load(27).orElseThrow());
    }
}
