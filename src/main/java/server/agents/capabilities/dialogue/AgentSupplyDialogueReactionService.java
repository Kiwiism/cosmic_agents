package server.agents.capabilities.dialogue;

import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyUrgency;
import server.agents.capabilities.supplies.AgentSupplyThresholdChangedEvent;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventBus;
import server.agents.events.AgentEventListener;
import server.agents.events.AgentEventPriority;

import java.util.Map;

/** Turns operational supply facts into optional, observer-gated presentation intents. */
public final class AgentSupplyDialogueReactionService implements AgentEventListener<AgentEvent> {
    public static final String INTENT_KEY = "supply.threshold";
    private static final long COOLDOWN_MS = 60_000L;

    private final AgentEventBus bus;

    public AgentSupplyDialogueReactionService(AgentEventBus bus) {
        this.bus = bus;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (!(event instanceof AgentSupplyThresholdChangedEvent threshold)
                || threshold.urgency().ordinal() < AgentSupplyUrgency.LOW.ordinal()
                || !supportsDialogue(threshold.category())) {
            return;
        }
        bus.publish(new AgentDialogueIntentEvent(
                        threshold.agentId(),
                        threshold.occurredAtMs(),
                        INTENT_KEY,
                        AgentDialogueAudience.NEARBY_REAL_PLAYER,
                        "supply:" + threshold.category(),
                        COOLDOWN_MS,
                        Map.of(
                                "category", threshold.category().name(),
                                "urgency", threshold.urgency().name(),
                                "current", String.valueOf(threshold.currentQuantity()),
                                "target", String.valueOf(threshold.targetQuantity()),
                                "objectiveId", threshold.objectiveId())),
                AgentEventPriority.NORMAL);
    }

    private static boolean supportsDialogue(AgentResourceCategory category) {
        return switch (category) {
            case HP_POTION, MP_POTION, ARROW, CROSSBOW_BOLT, THROWING_STAR, BULLET -> true;
            default -> false;
        };
    }
}
