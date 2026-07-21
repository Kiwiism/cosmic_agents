package server.agents.resources.events;

import server.agents.capabilities.dialogue.AgentDialogueAudience;
import server.agents.capabilities.dialogue.AgentDialogueIntentEvent;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventBus;
import server.agents.events.AgentEventListener;
import server.agents.events.AgentEventPriority;

import java.util.Map;

/** Converts notable resource facts into optional observer-facing dialogue intents. */
public final class AgentResourceDialogueReactionService implements AgentEventListener<AgentEvent> {
    public static final String INVENTORY_FULL_INTENT = "resource.inventory-full";
    public static final String SCROLL_INTENT = "resource.scroll-result";
    private static final long INVENTORY_COOLDOWN_MS = 60_000L;
    private static final long SCROLL_COOLDOWN_MS = 15_000L;

    private final AgentEventBus bus;

    public AgentResourceDialogueReactionService(AgentEventBus bus) {
        this.bus = bus;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        AgentDialogueIntentEvent intent = switch (event) {
            case AgentInventoryThresholdChangedEvent threshold
                    when "FULL".equals(threshold.threshold()) -> new AgentDialogueIntentEvent(
                    threshold.agentId(), threshold.occurredAtMs(), INVENTORY_FULL_INTENT,
                    AgentDialogueAudience.NEARBY_REAL_PLAYER,
                    "inventory-full:" + threshold.inventoryType(), INVENTORY_COOLDOWN_MS,
                    Map.of("inventoryType", threshold.inventoryType()));
            case AgentScrollResolvedEvent scroll -> new AgentDialogueIntentEvent(
                    scroll.agentId(), scroll.occurredAtMs(), SCROLL_INTENT,
                    AgentDialogueAudience.NEARBY_REAL_PLAYER, "scroll-result",
                    SCROLL_COOLDOWN_MS, Map.of("result", scroll.result()));
            default -> null;
        };
        if (intent != null) {
            bus.publish(intent, AgentEventPriority.AMBIENT);
        }
    }
}
