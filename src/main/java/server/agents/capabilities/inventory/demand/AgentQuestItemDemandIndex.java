package server.agents.capabilities.inventory.demand;

import java.util.List;

/** Versioned, generated quest-item demand facts. This type contains no runtime policy. */
public record AgentQuestItemDemandIndex(
        int schemaVersion,
        String catalogId,
        String revision,
        List<Integer> demandHorizons,
        List<Entry> entries) {

    public AgentQuestItemDemandIndex {
        if (schemaVersion <= 0 || blank(catalogId) || blank(revision)
                || demandHorizons == null || entries == null) {
            throw new IllegalArgumentException("complete quest-item demand index is required");
        }
        demandHorizons = List.copyOf(demandHorizons);
        entries = List.copyOf(entries);
    }

    public record Entry(int itemId, String itemName, int totalRequiredCount, List<QuestDemand> quests) {
        public Entry {
            if (itemId <= 0 || totalRequiredCount <= 0 || quests == null || quests.isEmpty()) {
                throw new IllegalArgumentException("valid quest-item demand entry is required");
            }
            itemName = itemName == null ? "" : itemName;
            quests = List.copyOf(quests);
        }
    }

    public record QuestDemand(
            int questId,
            String questName,
            int requiredCount,
            Integer minLevel,
            Integer maxLevel,
            List<Integer> jobs,
            List<Prerequisite> prerequisiteRequirements,
            boolean autonomousStartAllowed,
            String selectionDisposition) {
        public QuestDemand {
            if (questId <= 0 || requiredCount <= 0) {
                throw new IllegalArgumentException("valid quest demand is required");
            }
            questName = questName == null ? "" : questName;
            jobs = jobs == null ? List.of() : List.copyOf(jobs);
            prerequisiteRequirements = prerequisiteRequirements == null
                    ? List.of() : List.copyOf(prerequisiteRequirements);
            selectionDisposition = selectionDisposition == null ? "" : selectionDisposition;
        }
    }

    public record Prerequisite(int questId, int state) {
        public Prerequisite {
            if (questId <= 0 || state < 0 || state > 2) {
                throw new IllegalArgumentException("valid quest prerequisite is required");
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
