package server.agents.capabilities.build.profiles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentApBuildProfileStateTest {
    @Test
    void clearHandsAllocationToTheExpeditionSpecificSecondaryStatBuild() {
        AgentApBuildProfileState state = new AgentApBuildProfileState();
        state.assign(new AgentApBuildProfile("first-job", 1,
                AgentApBuildProfile.JobFamily.WARRIOR,
                AgentApBuildProfile.StatType.STR, AgentApBuildProfile.StatType.DEX,
                10, 10, 1, 30, 30));

        assertTrue(state.hasProfile());
        state.clear();

        assertFalse(state.hasProfile());
        assertNull(state.profile());
    }
}
