package server.agents.objectives;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAgentObjectiveCheckpointStoreTest {
    @TempDir
    Path temp;

    @Test
    void roundTripsActiveAndSuspendedIntentAndDeletesIt() throws Exception {
        FileAgentObjectiveCheckpointStore store = new FileAgentObjectiveCheckpointStore(temp);
        AgentObjectiveDefinition active = objective("maintenance", "maintenance.resupply");
        AgentObjectiveDefinition foreground = objective("training", "progression.victoria-training");
        AgentObjectiveCheckpoint checkpoint = new AgentObjectiveCheckpoint(
                1, 77, 500L, active,
                List.of(new AgentObjectiveSuspension(foreground, "potions critical", 400L)),
                List.of(new AgentObjectiveJournalEntry(400L, foreground.objectiveId(),
                        AgentObjectiveStatus.SUSPENDED, "potions critical",
                        foreground.source(), foreground.behaviorVersion(), foreground.correlationId())));

        assertTrue(store.load(77).isEmpty());
        store.save(checkpoint);
        assertEquals(checkpoint, store.load(77).orElseThrow());
        store.delete(77);
        assertTrue(store.load(77).isEmpty());
    }

    private static AgentObjectiveDefinition objective(String id, String type) {
        return new AgentObjectiveDefinition(id, type, 10, 10_000L, 2,
                AgentObjectiveSource.PROGRESSION_POLICY, "v1", "run-77");
    }
}
