package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AgentRuntimeIdentityStateTest {
    @Test
    void keepsAgentStableAndAllowsLeaderRefresh() {
        Character agent = mock(Character.class);
        Character firstLeader = mock(Character.class);
        Character refreshedLeader = mock(Character.class);
        AgentRuntimeIdentityState state = new AgentRuntimeIdentityState(agent, firstLeader);

        assertSame(agent, state.agent());
        assertSame(firstLeader, state.leader());

        state.setLeader(refreshedLeader);

        assertSame(agent, state.agent());
        assertSame(refreshedLeader, state.leader());
    }
}
