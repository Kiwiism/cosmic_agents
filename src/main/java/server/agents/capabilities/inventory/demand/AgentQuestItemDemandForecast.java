package server.agents.capabilities.inventory.demand;

import java.util.List;
import java.util.Map;

/** Explainable read-only demand forecast; consumers must separately authorize mutations. */
public record AgentQuestItemDemandForecast(
        String catalogId,
        String revision,
        List<ItemForecast> items) {

    public AgentQuestItemDemandForecast {
        catalogId = catalogId == null ? "" : catalogId;
        revision = revision == null ? "" : revision;
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record ItemForecast(
            int itemId,
            String itemName,
            int ownedQuantity,
            Map<AgentQuestDemandCategory, Integer> demandByCategory,
            List<QuestEvidence> evidence) {
        public ItemForecast {
            itemName = itemName == null ? "" : itemName;
            demandByCategory = demandByCategory == null ? Map.of() : Map.copyOf(demandByCategory);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }

        public int demand(AgentQuestDemandCategory category) {
            return demandByCategory.getOrDefault(category, 0);
        }

        public int authoritativeDemand() {
            return demand(AgentQuestDemandCategory.ACTIVE)
                    + demand(AgentQuestDemandCategory.COMMITTED);
        }

        public int forecastDemand() {
            return demandByCategory.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public record QuestEvidence(
            int questId,
            String questName,
            int requiredCount,
            AgentQuestDemandCategory category,
            String reason) {
        public QuestEvidence {
            questName = questName == null ? "" : questName;
            reason = reason == null ? "" : reason;
        }
    }
}
