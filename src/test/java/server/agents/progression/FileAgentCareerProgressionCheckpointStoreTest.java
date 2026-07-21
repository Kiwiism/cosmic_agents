package server.agents.progression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAgentCareerProgressionCheckpointStoreTest {
    @TempDir
    Path temp;

    @Test
    void roundTripsTypedCareerCursorAndDeletesIt() throws Exception {
        FileAgentCareerProgressionCheckpointStore store =
                new FileAgentCareerProgressionCheckpointStore(temp);
        AgentCareerProgressionCheckpoint checkpoint = new AgentCareerProgressionCheckpoint(
                1, 91, "thief-claw-standard-v1", 1,
                AgentCareerProgressionState.RunMode.LEVEL15_WITH_INITIAL_SHOP,
                "lv9-grind", AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING,
                2, 1, 2_000L, "", 7L, 1_000L);

        assertTrue(store.load(91).isEmpty());
        store.save(checkpoint);
        assertEquals(checkpoint, store.load(91).orElseThrow());
        store.delete(91);
        assertTrue(store.load(91).isEmpty());
    }
}
