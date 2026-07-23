package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.townlife.AgentTownLifeEncounterEvent;
import server.agents.capabilities.townlife.AgentTownLifeEncounterState;
import server.agents.events.AgentEvent;
import server.agents.events.BoundedAgentEventBus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentTownLifeDialogueReactionServiceTest {
    @Test
    void onlyAStableSubsetOfInitiatorsProduceObserverGatedDialogueIntents() {
        BoundedAgentEventBus bus = new BoundedAgentEventBus();
        AgentTownLifeDialogueReactionService listener = new AgentTownLifeDialogueReactionService(bus);
        List<AgentEvent> intents = new ArrayList<>();
        bus.subscribe(AgentDialogueIntentEvent.TYPE, intents::add);

        for (int index = 0; index < 20 && intents.isEmpty(); index++) {
            listener.onAgentEvent(encounter("encounter-" + index,
                    AgentTownLifeEncounterState.Role.INITIATOR,
                    AgentTownLifeEncounterState.Phase.ACTIVE));
            bus.drain(10);
        }

        assertFalse(intents.isEmpty());
        AgentDialogueIntentEvent intent = (AgentDialogueIntentEvent) intents.getFirst();
        assertEquals(AgentDialogueAudience.NEARBY_REAL_PLAYER, intent.audience());
        assertEquals("townlife-ambient", intent.dedupeKey());
    }

    @Test
    void responderAndNonActivePhasesStaySilent() {
        BoundedAgentEventBus bus = new BoundedAgentEventBus();
        AgentTownLifeDialogueReactionService listener = new AgentTownLifeDialogueReactionService(bus);

        listener.onAgentEvent(encounter("responder", AgentTownLifeEncounterState.Role.RESPONDER,
                AgentTownLifeEncounterState.Phase.ACTIVE));
        listener.onAgentEvent(encounter("approaching", AgentTownLifeEncounterState.Role.INITIATOR,
                AgentTownLifeEncounterState.Phase.APPROACHING));

        assertEquals(0, bus.snapshot().queued());
    }

    private static AgentTownLifeEncounterEvent encounter(
            String id,
            AgentTownLifeEncounterState.Role role,
            AgentTownLifeEncounterState.Phase phase) {
        return new AgentTownLifeEncounterEvent(
                11, 1_000L, 104000000, id,
                AgentTownLifeEncounterState.Type.SOCIAL_CHAT, role, phase,
                22, 11, "central-benches", id);
    }
}
