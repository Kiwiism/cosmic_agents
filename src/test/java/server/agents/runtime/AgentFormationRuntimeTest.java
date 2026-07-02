package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFormationRuntimeTest {
    @Test
    void defaultFormationUsesRuntimeConfigValues() {
        AgentFormationService.FormationState state = AgentFormationRuntime.defaultFormationState();

        assertEquals(AgentFormationService.FormationType.STAGGER, state.type());
        assertEquals(AgentRuntimeConfig.cfg.FOLLOW_STAGGER, state.px());
    }

    @Test
    void setsAndReadsLeaderFormationState() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);
        when(leader.getId()).thenReturn(441);
        BotEntry entry = new BotEntry(agent, leader, null);

        AgentFormationRuntime.setFormationState(
                leader,
                AgentFormationService.FormationType.SPREAD,
                90,
                15,
                List.of(entry));

        AgentFormationService.FormationState state = AgentFormationRuntime.formationStateFor(entry);
        assertEquals(AgentFormationService.FormationType.SPREAD, state.type());
        assertEquals(90, state.px());
        assertEquals(15, state.snapRange());
        assertSame(state, AgentFormationService.formationsByLeaderId().get(441));
    }
}
