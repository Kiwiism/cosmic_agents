package server.agents.economy.decision;

import server.agents.economy.market.EconomicReason;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregates demand only from accepted, unfinished quest objectives observed on real agents. */
public final class QuestDemandForecaster {
    public List<DemandSignal> forecast(List<QuestObjectiveState> states) {
        Map<Key, Aggregate> totals = new LinkedHashMap<>();
        for (QuestObjectiveState state : states) {
            if (!state.accepted() || state.completed()) continue;
            int remaining = Math.max(0, state.requiredQuantity()
                    - Math.max(state.objectiveProgress(), state.ownedEligibleQuantity()));
            if (remaining == 0) continue;
            totals.computeIfAbsent(new Key(state.questId(), state.itemId()), ignored -> new Aggregate())
                    .add(remaining, state.urgency(), state.agentId());
        }
        List<DemandSignal> result = new ArrayList<>();
        totals.forEach((key, value) -> result.add(new DemandSignal(key.itemId, value.quantity,
                value.agentIds.size(), value.urgency / value.agentIds.size(),
                EconomicReason.QUEST_REQUIREMENT,
                "acceptedQuest=" + key.questId + " remainingObjective=" + value.quantity
                        + " agents=" + value.agentIds.size())));
        return List.copyOf(result);
    }

    public record QuestObjectiveState(String agentId, int questId, int itemId, int requiredQuantity,
                                      int objectiveProgress, int ownedEligibleQuantity,
                                      boolean accepted, boolean completed, double urgency) {
        public QuestObjectiveState {
            if (agentId == null || agentId.isBlank() || questId <= 0 || itemId <= 0
                    || requiredQuantity <= 0 || objectiveProgress < 0 || ownedEligibleQuantity < 0
                    || urgency < 0 || urgency > 1) throw new IllegalArgumentException();
        }
    }

    private record Key(int questId, int itemId) { }
    private static final class Aggregate {
        private long quantity;
        private double urgency;
        private final java.util.Set<String> agentIds = new java.util.HashSet<>();
        private void add(int amount, double value, String agentId) {
            quantity = Math.addExact(quantity, amount);
            urgency += value;
            agentIds.add(agentId);
        }
    }
}
