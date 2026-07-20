package server.agents.resources.events;

import server.agents.events.AgentContextualEvent;

import java.util.Map;

/** Aggregate equipped-slot transition after equipment policy runs. */
public record AgentEquipmentLoadoutChangedEvent(
        int agentId,
        long occurredAtMs,
        Map<Short, Integer> previousLoadout,
        Map<Short, Integer> loadout,
        String reason,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "equipment.loadout-changed";

    public AgentEquipmentLoadoutChangedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || previousLoadout == null || loadout == null
                || previousLoadout.equals(loadout) || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Valid equipment loadout transition is required");
        }
        previousLoadout = Map.copyOf(previousLoadout);
        loadout = Map.copyOf(loadout);
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
