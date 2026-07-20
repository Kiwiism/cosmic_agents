package server.agents.personality;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAgentPersonalityAssignmentStoreTest {
    @TempDir
    Path temp;

    @Test
    void atomicallyRoundTripsAssignmentByCharacterIdentity() throws Exception {
        FileAgentPersonalityAssignmentStore store =
                new FileAgentPersonalityAssignmentStore(temp);
        AgentPersonalityAssignment assignment = new AgentPersonalityAssignment(
                1, 77, "OldSchoolIGN", "explorer-v1", 1, 887766L, 1234L);

        assertTrue(store.load(77).isEmpty());
        store.save(assignment);

        assertEquals(assignment, store.load(77).orElseThrow());
    }
}
