package server.agents.progression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
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
                2, 77, "OldSchoolIGN", "thief-claw-standard-v1", 1,
                "quester-v1", 1234L);

        assertTrue(store.load(77).isEmpty());
        store.save(assignment);
        assertEquals(assignment, store.load(77).orElseThrow());
    }

    @Test
    void readsLegacyAssignmentWithoutProgressionProfileForOneTimeMigration() throws Exception {
        Files.writeString(temp.resolve("78.json"), """
                {
                  "schemaVersion": 1,
                  "characterId": 78,
                  "characterName": "LegacyIGN",
                  "bundleId": "warrior-standard-v1",
                  "bundleVersion": 1,
                  "assignedAtMs": 1234
                }
                """);
        FileAgentCareerAssignmentStore store = new FileAgentCareerAssignmentStore(temp);

        AgentCareerAssignment assignment = store.load(78).orElseThrow();

        assertEquals("", assignment.progressionProfileId());
    }
}
