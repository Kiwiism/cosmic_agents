package server.agents.capabilities.dialogue;

import server.agents.capabilities.townlife.AgentTownLifeEncounterEvent;
import server.agents.capabilities.townlife.AgentTownLifeEncounterState;
import server.agents.capabilities.townlife.AgentTownLifeArrivalEvent;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventBus;
import server.agents.events.AgentEventListener;
import server.agents.events.AgentEventPriority;

import java.util.Map;

/** Converts a small deterministic subset of social encounters into observer-gated speech intents. */
public final class AgentTownLifeDialogueReactionService implements AgentEventListener<AgentEvent> {
    public static final String SOCIAL_INTENT = "townlife.social";
    public static final String SPARRING_INTENT = "townlife.sparring";
    public static final String ARRIVAL_INTENT = "townlife.arrival";
    private static final long AMBIENT_CHAT_COOLDOWN_MS = config.AgentTuning.longValue("server.agents.capabilities.dialogue.AgentTownLifeDialogueReactionService.AMBIENT_CHAT_COOLDOWN_MS");
    private final AgentEventBus eventBus;

    public AgentTownLifeDialogueReactionService(AgentEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (event instanceof AgentTownLifeArrivalEvent arrival) {
            int variant = Math.floorMod(
                    31 * arrival.agentId() + arrival.profileId().hashCode(), 4);
            eventBus.publish(new AgentDialogueIntentEvent(
                    arrival.agentId(), arrival.occurredAtMs(), ARRIVAL_INTENT,
                    AgentDialogueAudience.NEARBY_REAL_PLAYER,
                    "townlife-arrival", AMBIENT_CHAT_COOLDOWN_MS,
                    Map.of(
                            "mapId", String.valueOf(arrival.mapId()),
                            "purpose", arrival.purpose().name(),
                            "reason", arrival.reason(),
                            "variant", String.valueOf(variant))),
                    AgentEventPriority.AMBIENT);
            return;
        }
        if (!(event instanceof AgentTownLifeEncounterEvent encounter)
                || encounter.phase() != AgentTownLifeEncounterState.Phase.ACTIVE
                || encounter.participantRole() != AgentTownLifeEncounterState.Role.INITIATOR
                || encounter.turnOwnerAgentId() != encounter.agentId()
                || !eligible(encounter)) {
            return;
        }
        String intent = encounter.encounterType() == AgentTownLifeEncounterState.Type.PLAYFUL_SPARRING
                ? SPARRING_INTENT : SOCIAL_INTENT;
        int variant = Math.floorMod((encounter.encounterId() + ':' + encounter.agentId()).hashCode(), 4);
        eventBus.publish(new AgentDialogueIntentEvent(
                encounter.agentId(), encounter.occurredAtMs(), intent,
                AgentDialogueAudience.NEARBY_REAL_PLAYER,
                "townlife-ambient", AMBIENT_CHAT_COOLDOWN_MS,
                Map.of(
                        "encounterId", encounter.encounterId(),
                        "venueId", encounter.venueId(),
                        "peerAgentId", String.valueOf(encounter.peerAgentId()),
                        "variant", String.valueOf(variant))),
                AgentEventPriority.AMBIENT);
    }

    private static boolean eligible(AgentTownLifeEncounterEvent encounter) {
        int mixed = encounter.encounterId().hashCode() ^ Integer.rotateLeft(encounter.agentId(), 11);
        return Math.floorMod(mixed, 4) == 0;
    }
}
