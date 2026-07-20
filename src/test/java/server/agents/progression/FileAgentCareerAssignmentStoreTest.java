package server.agents.progression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAgentCareerAssignmentStoreTest {
    @TempDir
    Path temp;

    @Test
    void roundTripsAssignmentByCharacterIdentity() throws Exception {
        FileAgentCareerAssignmentStore store = new FileAgentCareerAssignmentStore(temp);
        AgentCareerAssignment assignment = new AgentCareerAssignment(
                1, 77, "OldSchoolIGN", "thief-claw-standard-v1", 1, 1234L);

        assertTrue(store.load(77).isEmpty());
        store.save(assignment);
        assertEquals(assignment, store.load(77).orElseThrow());
    }
}
