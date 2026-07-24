package server.agents.plans;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
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
                Map.of("mode", "auto"), "", List.of(),
                "southperry-to-lith-harbor", 456L,
                "", 4L, 500L);

        store.save(checkpoint);

        assertEquals(checkpoint, store.load(91).orElseThrow());
        store.delete(91);
        assertTrue(store.load(91).isEmpty());
    }

    @Test
    void loadsCheckpointWrittenBeforeDeferredSuccessorsWereAdded() throws Exception {
        Files.createDirectories(temp);
        Files.writeString(temp.resolve("92.json"), """
                {
                  "schemaVersion": 1,
                  "characterId": 92,
                  "planId": "maple-island-full-mvp",
                  "planVersion": "1",
                  "chainId": "chain:92:1",
                  "stepIndex": 0,
                  "stepStarted": false,
                  "stepAttempt": 0,
                  "stepStartedAtMs": 0,
                  "status": "ACTIVE",
                  "inputs": {},
                  "pendingSuccessorPlanId": "",
                  "availableSuccessorPlanIds": [],
                  "nextActionAtMs": 0,
                  "reason": "",
                  "stateRevision": 1,
                  "updatedAtMs": 1
                }
                """);

        AgentPlanCheckpoint checkpoint =
                new FileAgentPlanCheckpointStore(temp).load(92).orElseThrow();

        assertEquals("", checkpoint.deferredSuccessorPlanId());
    }
}
