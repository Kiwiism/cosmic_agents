package server.agents.personality;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.model.AgentId;
import server.agents.model.AgentIdentity;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPersonalityAssignmentServiceTest {
    @TempDir
    Path temp;

    @Test
    void restoresTheSameDurableProfileAndBehaviorSeedAcrossSessions() throws Exception {
        AgentIdentity identity = new AgentIdentity(new AgentId(91), "SleepySin");
        AgentPersonalityState first = new AgentPersonalityState();
        AgentPersonalityState second = new AgentPersonalityState();
        FileAgentPersonalityAssignmentStore store =
                new FileAgentPersonalityAssignmentStore(temp);
        AgentPersonalityProfileRepository profiles =
                AgentPersonalityProfileRepository.defaultRepository();

        AgentPersonalityProfile assigned = AgentPersonalityAssignmentService.restoreOrAssign(
                first, identity, true, 500L, profiles, store);
        AgentPersonalityProfile restored = AgentPersonalityAssignmentService.restoreOrAssign(
                second, identity, true, 900L, profiles, store);

        assertEquals(assigned, restored);
        assertEquals(first.assignment(), second.assignment());
        assertEquals(first.behaviorSeed(), second.behaviorSeed());
        assertTrue(second.presentationEnabled());
    }
}
