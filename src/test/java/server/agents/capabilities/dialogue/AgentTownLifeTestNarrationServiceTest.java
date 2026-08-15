package server.agents.capabilities.dialogue;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.townlife.AgentTownLifeActivityEvent;
import server.agents.capabilities.townlife.AgentTownLifeState;
import server.agents.events.AgentEvent;
import server.agents.events.BoundedAgentEventBus;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.townlife.AgentTownLifeTestObservationState;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTownLifeTestNarrationServiceTest {
    @Test
    void performingAnnouncementIncludesTheCommittedRemainingDuration() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(501);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        state.start(1_000L, 501, 104000000);
        state.select(AgentTownLifeState.Activity.REST, new Point(2_404, 525),
                0, "venue:harbor-benches", "harbor-benches", "test", "decision-1", 1_100L);
        state.beginDwell(10_500L);
        entry.capabilityStates().require(AgentTownLifeTestObservationState.STATE_KEY)
                .enable("scenario-1");
        BoundedAgentEventBus bus = new BoundedAgentEventBus();
        List<AgentEvent> intents = new ArrayList<>();
        bus.subscribe(AgentDialogueIntentEvent.TYPE, intents::add);
        AgentTownLifeTestNarrationService narrator =
                new AgentTownLifeTestNarrationService(entry, bus);

        narrator.onAgentEvent(new AgentTownLifeActivityEvent(
                501, 2_000L, 104000000, "lith-harbor",
                AgentTownLifeState.Activity.REST,
                AgentTownLifeActivityEvent.Phase.ORIENTING,
                "harbor-benches", 0, "test", "decision-1"));
        bus.drain(10);

        AgentDialogueIntentEvent intent = (AgentDialogueIntentEvent) intents.getFirst();
        assertEquals(AgentTownLifeTestNarrationService.ACTIVITY_INTENT, intent.intentKey());
        assertEquals("9", intent.parameters().get("remainingSeconds"));
        assertEquals("REST", intent.parameters().get("activity"));
    }
}
