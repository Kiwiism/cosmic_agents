package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotFormationStateRuntime;
import server.agents.runtime.AgentFormationService.FormationState;
import server.agents.runtime.AgentFormationService.FormationType;
import server.bots.BotEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentFormationServiceTest {
    @Test
    void preservesLegacyOffsetPatterns() {
        assertEquals(60, new FormationState(FormationType.STAGGER, 60, 120).offsetFor(0, 4));
        assertEquals(-60, new FormationState(FormationType.STAGGER, 60, 120).offsetFor(1, 4));
        assertEquals(120, new FormationState(FormationType.STAGGER, 60, 120).offsetFor(2, 4));
        assertEquals(-120, new FormationState(FormationType.STAGGER, 60, 120).offsetFor(3, 4));
        assertEquals(0, new FormationState(FormationType.STACK, 0, 120).offsetFor(3, 4));
        assertEquals(0, new FormationState(FormationType.SPREAD, 70, 120).offsetFor(0, 4));
        assertEquals(70, new FormationState(FormationType.SPREAD, 70, 120).offsetFor(1, 4));
        assertEquals(-70, new FormationState(FormationType.SPREAD, 70, 120).offsetFor(2, 4));
        assertEquals(-120, new FormationState(FormationType.LEFT, 120, 120).offsetFor(0, 4));
        assertEquals(240, new FormationState(FormationType.RIGHT, 120, 120).offsetFor(1, 4));
    }

    @Test
    void appliesOffsetsToEntries() {
        BotEntry first = new BotEntry(mock(Character.class), mock(Character.class), null);
        BotEntry second = new BotEntry(mock(Character.class), mock(Character.class), null);

        AgentFormationService.applyOffsets(
                List.of(first, second), new FormationState(FormationType.STAGGER, 60, 120));

        assertEquals(60, AgentBotFormationStateRuntime.followOffsetX(first));
        assertEquals(-60, AgentBotFormationStateRuntime.followOffsetX(second));
    }
}
