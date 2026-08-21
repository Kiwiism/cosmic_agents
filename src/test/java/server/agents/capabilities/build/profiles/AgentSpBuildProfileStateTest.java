package server.agents.capabilities.build.profiles;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSpBuildProfileStateTest {
    @Test
    void clearHandsAllocationBackToTheLegacyJobBuild() {
        AgentSpBuildProfileState state = new AgentSpBuildProfileState();
        state.assign(new AgentSpBuildProfile("first-job", 1,
                AgentSpBuildProfile.JobFamily.WARRIOR, 30,
                List.of(new AgentSpBuildProfile.LevelPlan(10,
                        List.of(new AgentSpBuildProfile.SkillPoints(1001004, 1))))));

        assertTrue(state.hasProfile());
        state.clear();

        assertFalse(state.hasProfile());
        assertNull(state.profile());
    }
}
