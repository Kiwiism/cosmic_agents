package server.agents.progression;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentMushroomKingdomEventListenerTest {
    @Test
    void countsOnlyHelmetPepeKillsDuringTheRareItemObjective() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMushroomKingdomState state = entry.capabilityStates()
                .require(AgentMushroomKingdomState.STATE_KEY);
        state.begin(1L);
        state.observe(2326, 0, 106021100, new Point(), 2L);
        AgentMushroomKingdomEventListener listener = new AgentMushroomKingdomEventListener(entry);

        listener.onAgentEvent(new AgentMobKilledEvent(
                1, 3L, 106021100, 3300003, 10, 35, "mushroom-kingdom:2326"));
        listener.onAgentEvent(new AgentMobKilledEvent(
                1, 4L, 106021100, 3300004, 11, 35, "mushroom-kingdom:2326"));

        assertEquals(1, state.helmetPepeKills());
    }
}
