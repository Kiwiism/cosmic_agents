package server.agents.plans;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAgentPlanCheckpointStoreTest {
    @TempDir
    Path temp;

    @Test
    void roundTripsUniversalCursorWithoutTransientAttachments() throws Exception {
        FileAgentPlanCheckpointStore store = new FileAgentPlanCheckpointStore(temp);
        AgentPlanCheckpoint checkpoint = new AgentPlanCheckpoint(
                1, 91, "maple-island-full-mvp", "1", "chain:91:1",
                0, true, 1, 123L, AgentPlanExecutionStatus.ACTIVE,
                Map.of("mode", "auto"), "", List.of(), 456L,
                "", 4L, 500L);

        store.save(checkpoint);

        assertEquals(checkpoint, store.load(91).orElseThrow());
        store.delete(91);
        assertTrue(store.load(91).isEmpty());
    }
}
