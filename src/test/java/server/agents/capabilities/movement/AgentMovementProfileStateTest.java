package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMovementProfileStateTest {
    @Test
    void defaultsToBaseAndNormalizesNull() {
        AgentMovementProfileState state = new AgentMovementProfileState();

        assertEquals(AgentMovementProfile.base(), state.profile());

        AgentMovementProfile profile = new AgentMovementProfile(147, 119);
        state.setProfile(profile);
        assertEquals(new AgentMovementProfile(145, 115), state.profile());

        state.setProfile(null);
        assertEquals(AgentMovementProfile.base(), state.profile());
    }
}
