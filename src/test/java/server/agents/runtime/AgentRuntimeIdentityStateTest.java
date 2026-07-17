package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AgentRuntimeIdentityStateTest {
    @Test
    void keepsOnlyTheStableAgentIdentity() {
        Character agent = mock(Character.class);
        AgentRuntimeIdentityState state = new AgentRuntimeIdentityState(agent);

        assertSame(agent, state.agent());
    }
}
