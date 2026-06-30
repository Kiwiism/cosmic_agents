package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import client.Character;
import client.Skill;
import org.junit.jupiter.api.Test;

class AgentSupportSpecialMoveExecutorTest {
    @Test
    void shouldRejectDispatchWhenAgentHasNoClient() {
        Character agent = mock(Character.class);
        Skill skill = mock(Skill.class);
        when(agent.getClient()).thenReturn(null);

        assertFalse(AgentSupportSpecialMoveExecutor.dispatch(agent, skill, 1));
    }
}
