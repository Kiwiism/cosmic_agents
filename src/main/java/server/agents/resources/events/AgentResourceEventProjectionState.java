package server.agents.resources.events;

import server.agents.events.AgentEvent;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Bounded per-session resource read model for diagnostics and future policy inputs. */
public final class AgentResourceEventProjectionState {
    public static final AgentCapabilityStateKey<AgentResourceEventProjectionState> STATE_KEY =
            new AgentCapabilityStateKey<>("resources.event-projection",
                    AgentResourceEventProjectionState.class,
                    AgentResourceEventProjectionState::new);

    private long itemTransitions;
    private long lootCollections;
    private long inventoryThresholds;
    private long equipmentCandidates;
    private long loadoutChanges;
    private long shopTransactions;
    private long scrollOutcomes;
    private long revision;
    private AgentEvent lastEvent;

    public synchronized void record(AgentEvent event) {
        if (event instanceof AgentItemQuantityChangedEvent) {
            itemTransitions++;
        } else if (event instanceof AgentLootCollectedEvent) {
            lootCollections++;
        } else if (event instanceof AgentInventoryThresholdChangedEvent) {
            inventoryThresholds++;
        } else if (event instanceof AgentEquipmentCandidateDetectedEvent) {
            equipmentCandidates++;
        } else if (event instanceof AgentEquipmentLoadoutChangedEvent) {
            loadoutChanges++;
        } else if (event instanceof AgentShopTransactionEvent) {
            shopTransactions++;
        } else if (event instanceof AgentScrollResolvedEvent) {
            scrollOutcomes++;
        } else {
            return;
        }
        revision++;
        lastEvent = event;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(itemTransitions, lootCollections, inventoryThresholds,
                equipmentCandidates, loadoutChanges, shopTransactions, scrollOutcomes,
                revision, lastEvent);
    }

    public record Snapshot(long itemTransitions,
                           long lootCollections,
                           long inventoryThresholds,
                           long equipmentCandidates,
                           long loadoutChanges,
                           long shopTransactions,
                           long scrollOutcomes,
                           long revision,
                           AgentEvent lastEvent) {
    }
}
