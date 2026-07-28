package server.agents.capabilities.inventory.demand;

import client.QuestStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pure filtering and aggregation over generated facts plus one character snapshot. */
public final class AgentQuestItemDemandForecastService {
    private final AgentQuestItemDemandIndexRepository repository;

    public AgentQuestItemDemandForecastService(AgentQuestItemDemandIndexRepository repository) {
        this.repository = repository;
    }

    public static AgentQuestItemDemandForecastService defaultService() {
        return new AgentQuestItemDemandForecastService(
                AgentQuestItemDemandIndexRepository.defaultRepository());
    }

    public AgentQuestItemDemandForecast forecast(AgentQuestDemandProfile profile) {
        List<AgentQuestItemDemandForecast.ItemForecast> items = new ArrayList<>();
        for (AgentQuestItemDemandIndex.Entry entry : repository.index().entries()) {
            EnumMap<AgentQuestDemandCategory, Integer> counts =
                    new EnumMap<>(AgentQuestDemandCategory.class);
            List<AgentQuestItemDemandForecast.QuestEvidence> evidence = new ArrayList<>();
            for (AgentQuestItemDemandIndex.QuestDemand demand : entry.quests()) {
                AgentQuestDemandCategory category = category(profile, demand);
                if (category == null) {
                    continue;
                }
                counts.merge(category, demand.requiredCount(), Integer::sum);
                evidence.add(new AgentQuestItemDemandForecast.QuestEvidence(
                        demand.questId(), demand.questName(), demand.requiredCount(), category,
                        reason(category)));
            }
            if (!counts.isEmpty()) {
                items.add(new AgentQuestItemDemandForecast.ItemForecast(
                        entry.itemId(), entry.itemName(),
                        profile.ownedItemCounts().getOrDefault(entry.itemId(), 0),
                        counts, evidence));
            }
        }
        AgentQuestItemDemandIndex index = repository.index();
        return new AgentQuestItemDemandForecast(index.catalogId(), index.revision(), items);
    }

    private static AgentQuestDemandCategory category(
            AgentQuestDemandProfile profile,
            AgentQuestItemDemandIndex.QuestDemand demand) {
        int status = profile.questStatus(demand.questId());
        if (status == QuestStatus.Status.COMPLETED.getId()) {
            return null;
        }
        if (status == QuestStatus.Status.STARTED.getId()) {
            return AgentQuestDemandCategory.ACTIVE;
        }
        if (!jobEligible(profile, demand) || !prerequisitesEligible(profile, demand)) {
            return null;
        }
        if (profile.committedQuestIds().contains(demand.questId())) {
            return AgentQuestDemandCategory.COMMITTED;
        }
        if (!demand.autonomousStartAllowed()
                || "review-blocked".equals(demand.selectionDisposition())) {
            return null;
        }
        if (demand.maxLevel() != null && profile.level() > demand.maxLevel()) {
            return null;
        }
        int levelsAway = demand.minLevel() == null
                ? 0 : Math.abs(demand.minLevel() - profile.level());
        if (levelsAway <= 5) {
            return AgentQuestDemandCategory.WITHIN_5_LEVELS;
        }
        if (levelsAway <= 15) {
            return AgentQuestDemandCategory.WITHIN_15_LEVELS;
        }
        if (levelsAway <= 25) {
            return AgentQuestDemandCategory.WITHIN_25_LEVELS;
        }
        return null;
    }

    private static boolean jobEligible(
            AgentQuestDemandProfile profile,
            AgentQuestItemDemandIndex.QuestDemand demand) {
        return demand.jobs().isEmpty()
                || demand.jobs().contains(profile.jobId())
                || demand.jobs().stream().anyMatch(profile.plannedJobIds()::contains);
    }

    private static boolean prerequisitesEligible(
            AgentQuestDemandProfile profile,
            AgentQuestItemDemandIndex.QuestDemand demand) {
        return demand.prerequisiteRequirements().stream().allMatch(prerequisite ->
                profile.questStatus(prerequisite.questId()) == prerequisite.state()
                        || (prerequisite.state() > 0
                        && profile.committedQuestIds().contains(prerequisite.questId())));
    }

    private static String reason(AgentQuestDemandCategory category) {
        return switch (category) {
            case ACTIVE -> "quest is active";
            case COMMITTED -> "quest is committed by the current plan";
            case WITHIN_5_LEVELS -> "eligible within plus or minus five levels";
            case WITHIN_15_LEVELS -> "eligible within plus or minus fifteen levels";
            case WITHIN_25_LEVELS -> "eligible within plus or minus twenty-five levels";
        };
    }
}
